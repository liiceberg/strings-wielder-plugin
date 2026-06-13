package com.liiceberg.strings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.liiceberg.utils.addImports
import org.jetbrains.kotlin.idea.util.isKotlinFileType
import org.jetbrains.kotlin.psi.*

class KotlinResourceUsageRewriter(
    private val project: Project,
) {

    private val psiManager = PsiManager.getInstance(project)
    private val ktPsiFactory = KtPsiFactory(project)

    fun rewrite(
        file: VirtualFile,
        changes: List<KotlinResourceUsageChange>,
    ): Boolean {
        if (!file.isKotlinFileType() || changes.isEmpty()) {
            return false
        }

        val psiFile = ApplicationManager.getApplication().runReadAction<KtFile?> {
            psiManager.findFile(file) as? KtFile
        } ?: return false

        var changed = false
        var shouldAddPluralImport = false

        WriteCommandAction.runWriteCommandAction(project) {
            psiFile.accept(object : KtTreeVisitorVoid() {
                override fun visitCallExpression(expression: KtCallExpression) {
                    super.visitCallExpression(expression)

                    val rewrite = changes.firstNotNullOfOrNull { change ->
                        expression.buildRewrite(change)
                    } ?: return

                    expression.replace(ktPsiFactory.createExpression(rewrite.text))
                    changed = true
                    shouldAddPluralImport = shouldAddPluralImport || rewrite.requiresPluralImport
                }
            })

            if (shouldAddPluralImport) {
                psiFile.addImports(listOf(PLURAL_RESOURCE_IMPORT), ktPsiFactory)
            }
        }

        return changed
    }

    private fun KtCallExpression.buildRewrite(change: KotlinResourceUsageChange): CallRewrite? {
        val callee = calleeExpression?.text ?: return null
        val resourceType = when (change) {
            is KotlinResourceUsageChange.Template -> STRING_RESOURCE_TYPE
            is KotlinResourceUsageChange.Plural -> STRING_RESOURCE_TYPE
        }
        val resourceArgumentIndex = valueArguments.indexOfFirst { argument ->
            argument.getArgumentExpression()?.isResourceReference(resourceType, change.sourceKey) == true
        }
        if (resourceArgumentIndex < 0) {
            return null
        }

        val resourceReference = valueArguments[resourceArgumentIndex].getArgumentExpression()?.text ?: return null

        return when (change) {
            is KotlinResourceUsageChange.Template -> buildTemplateRewrite(callee, resourceArgumentIndex, change)
            is KotlinResourceUsageChange.Plural -> buildPluralRewrite(callee, resourceReference, change)
        }
    }

    private fun KtCallExpression.buildTemplateRewrite(
        callee: String,
        resourceArgumentIndex: Int,
        change: KotlinResourceUsageChange.Template,
    ): CallRewrite? {
        if (callee !in TEMPLATE_SUPPORTED_CALLS || valueArguments.size > resourceArgumentIndex + 1) {
            return null
        }

        val arguments = valueArguments.take(resourceArgumentIndex + 1).map(KtValueArgument::getText) + change.arguments
        arguments[resourceArgumentIndex].replaceResourceKey(change.sourceKey, change.targetKey)
            ?.let { targetReference ->
                val updatedArguments = arguments.toMutableList()
                updatedArguments[resourceArgumentIndex] = targetReference
                return CallRewrite(
                    text = "$callee(${updatedArguments.joinToString(", ")})",
                    requiresPluralImport = false,
                )
            }
        return CallRewrite(
            text = "$callee(${arguments.joinToString(", ")})",
            requiresPluralImport = false,
        )
    }

    private fun KtCallExpression.buildPluralRewrite(
        callee: String,
        resourceReference: String,
        change: KotlinResourceUsageChange.Plural,
    ): CallRewrite? {
        val pluralReference = resourceReference.replaceResourceTypeAndKey(
            fromType = STRING_RESOURCE_TYPE,
            toType = PLURAL_RESOURCE_TYPE,
            fromKey = change.sourceKey,
            toKey = change.targetKey,
        )
            ?: return null

        return when (callee) {
            STRING_RESOURCE_CALL -> CallRewrite(
                text = "$PLURAL_RESOURCE_CALL($pluralReference, ${change.currentNumber})",
                requiresPluralImport = true,
            )

            GET_STRING_CALL -> CallRewrite(
                text = "${buildGetQuantityStringCallee()}($pluralReference, ${change.currentNumber})",
                requiresPluralImport = false,
            )

            else -> null
        }
    }

    private fun KtCallExpression.buildGetQuantityStringCallee(): String {
        val parentExpression = parent as? KtDotQualifiedExpression
        if (parentExpression?.selectorExpression == this && parentExpression.receiverExpression.text == RESOURCES_RECEIVER) {
            return GET_QUANTITY_STRING_CALL
        }

        return "$RESOURCES_RECEIVER.$GET_QUANTITY_STRING_CALL"
    }

    private fun String.replaceResourceTypeAndKey(
        fromType: String,
        toType: String,
        fromKey: String,
        toKey: String,
    ): String? {
        val parts = split(".").toMutableList()
        if (
            parts.size < MIN_RESOURCE_REFERENCE_PARTS ||
            parts[parts.lastIndex - 1] != fromType ||
            parts.last() != fromKey
        ) {
            return null
        }

        parts[parts.lastIndex - 1] = toType
        parts[parts.lastIndex] = toKey
        return parts.joinToString(".")
    }

    private fun String.replaceResourceKey(fromKey: String, toKey: String): String? {
        if (fromKey == toKey) {
            return null
        }

        val parts = split(".").toMutableList()
        if (parts.size < MIN_RESOURCE_REFERENCE_PARTS || parts.last() != fromKey) {
            return null
        }

        parts[parts.lastIndex] = toKey
        return parts.joinToString(".")
    }

    private fun KtExpression.isResourceReference(
        resourceType: String,
        key: String,
    ): Boolean {
        val parts = text.split(".")
        return parts.size >= MIN_RESOURCE_REFERENCE_PARTS &&
                parts.takeLast(MIN_RESOURCE_REFERENCE_PARTS) == listOf(R_CLASS, resourceType, key)
    }

    private data class CallRewrite(
        val text: String,
        val requiresPluralImport: Boolean,
    )

    private companion object {
        private const val R_CLASS = "R"
        private const val STRING_RESOURCE_TYPE = "string"
        private const val PLURAL_RESOURCE_TYPE = "plurals"
        private const val STRING_RESOURCE_CALL = "stringResource"
        private const val PLURAL_RESOURCE_CALL = "pluralStringResource"
        private const val GET_STRING_CALL = "getString"
        private const val GET_QUANTITY_STRING_CALL = "getQuantityString"
        private const val RESOURCES_RECEIVER = "resources"
        private const val PLURAL_RESOURCE_IMPORT = "androidx.compose.ui.res.pluralStringResource"
        private const val MIN_RESOURCE_REFERENCE_PARTS = 3
        private val TEMPLATE_SUPPORTED_CALLS = setOf(STRING_RESOURCE_CALL, GET_STRING_CALL)
    }
}

sealed interface KotlinResourceUsageChange {
    val sourceKey: String
    val targetKey: String

    data class Template(
        override val sourceKey: String,
        override val targetKey: String,
        val arguments: List<String>,
    ) : KotlinResourceUsageChange

    data class Plural(
        override val sourceKey: String,
        override val targetKey: String,
        val currentNumber: Int,
    ) : KotlinResourceUsageChange
}
