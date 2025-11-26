package com.liiceberg.utils.strings.finder

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.parents

class KotlinHardCodedStringFinder(project: Project) {

    private val psiManager = PsiManager.getInstance(project)

    fun findHardCodedStrings(virtualFile: VirtualFile): List<String> {
        psiManager.findFile(virtualFile)?.let { psiFile ->
            if (psiFile.isWritable) {
                return extractHardCodedString(psiFile)
            }
        }
        return emptyList()
    }

    private fun extractHardCodedString(file: PsiFile) : List<String> {
        file as KtFile
        val found = mutableListOf<String>()

        file.accept(object : KtTreeVisitorVoid() {

            override fun visitStringTemplateExpression(expression: KtStringTemplateExpression) {
                super.visitStringTemplateExpression(expression)

                val text = expression.text
                if (shouldInclude(expression).not()) return

                found += text.stripQuotes()
            }
        })

        return found
    }

    private fun shouldInclude(expr: KtStringTemplateExpression): Boolean {
        if (expr.text.stripQuotes().isBlank()) return false

        if (expr.isInsideFunctionWithAnnotation(COMPOSABLE).not() && isIncludedCall(expr).not()) return false
        if (expr.isInsideFunctionWithAnnotation(PREVIEW)) return false

        if (isExcludedCall(expr)) return false
        if (isExcludedArgument(expr)) return false

        return true
    }

    private fun isIncludedCall(expr: KtStringTemplateExpression) : Boolean {
        return expr.parents.any { parent ->
            when (parent) {
                is KtCallExpression -> {
                    parent.calleeExpression?.text?.contains(SET_CONTENT, ignoreCase = true) == true
                }
                is KtDotQualifiedExpression -> {
                    parent.selectorExpression?.text?.contains(SET_CONTENT, ignoreCase = true) == true
                }
                else -> false
            }
        }
    }

    private fun isExcludedCall(expr: KtStringTemplateExpression) : Boolean {
        val callExpression = expr.parents.filterIsInstance<KtCallExpression>().firstOrNull() ?: return false
        val calleeText = callExpression.calleeExpression?.text ?: return false

        val functions = listOf(LOG, TIMBER, PRINT, TEST_TAG, ITEM, EFFECT)

        if (functions.any { calleeText.contains(it, ignoreCase = true) }) {
            return true
        }

        val dotQualified = callExpression.parents.filterIsInstance<KtDotQualifiedExpression>().firstOrNull()
        val receiverText = dotQualified?.receiverExpression?.text ?: ""

        return functions.any { root -> receiverText.contains(root, ignoreCase = true) }
    }

    private fun isExcludedArgument(expr: KtStringTemplateExpression): Boolean {
        val valueArgument = expr.parents.filterIsInstance<KtValueArgument>().firstOrNull() ?: return false

        val argumentName = valueArgument.getArgumentName()?.asName?.asString() ?: return false

        val excludedArguments = listOf(TAG, CONTENT_DESCRIPTION, KEY)

        return excludedArguments.any { it.contains(argumentName, ignoreCase = true) }
    }

    private fun KtStringTemplateExpression.isInsideFunctionWithAnnotation(annotationValue: String): Boolean {
        return this.parents.any { parent ->
            if (parent is KtNamedFunction) {
                parent.annotationEntries.any { annotation ->
                    annotation.typeReference?.text?.contains(annotationValue) == true ||
                            annotation.text.contains(annotationValue)
                }
            } else {
                false
            }
        }
    }

    private fun String.stripQuotes() = this.removeSurrounding(QUOTE, QUOTE)

    private companion object {
        const val COMPOSABLE = "Composable"
        const val PREVIEW = "Preview"

        const val LOG = "Log"
        const val TIMBER = "Timber"
        const val PRINT = "print"

        const val TAG = "tag"
        const val TEST_TAG = "testTag"
        const val CONTENT_DESCRIPTION = "contentDescription"

        const val KEY = "key"
        const val ITEM = "item"
        const val EFFECT = "Effect"

        const val SET_CONTENT = "setContent"

        const val QUOTE = "\""
    }
}