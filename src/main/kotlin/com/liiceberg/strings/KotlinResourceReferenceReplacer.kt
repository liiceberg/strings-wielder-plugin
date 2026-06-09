package com.liiceberg.strings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import org.jetbrains.kotlin.idea.util.isKotlinFileType
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid

class KotlinResourceReferenceReplacer(
    private val project: Project,
) {

    private val psiManager = PsiManager.getInstance(project)
    private val ktPsiFactory = KtPsiFactory(project)

    fun replace(
        file: VirtualFile,
        replacements: List<KotlinResourceReferenceReplacement>,
    ): Boolean {
        if (!file.isKotlinFileType() || replacements.isEmpty()) {
            return false
        }

        val psiFile = ApplicationManager.getApplication().runReadAction<KtFile?> {
            psiManager.findFile(file) as? KtFile
        } ?: return false

        var changed = false
        WriteCommandAction.runWriteCommandAction(project) {
            psiFile.accept(object : KtTreeVisitorVoid() {
                override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
                    super.visitSimpleNameExpression(expression)

                    val replacement = replacements.firstOrNull { candidate ->
                        expression.getReferencedName() == candidate.fromKey &&
                            expression.isResourceReference(candidate)
                    } ?: return

                    expression.replace(ktPsiFactory.createExpression(replacement.toKey))
                    changed = true
                }
            })
        }

        return changed
    }

    private fun KtSimpleNameExpression.isResourceReference(
        replacement: KotlinResourceReferenceReplacement,
    ): Boolean {
        val parentExpression = parent as? KtDotQualifiedExpression ?: return false
        if (parentExpression.selectorExpression != this) {
            return false
        }

        val receiverParts = parentExpression.receiverExpression.text.split(".")
        if (receiverParts.size < MIN_RESOURCE_REFERENCE_PARTS) {
            return false
        }

        val expectedResourceType = when (replacement.resourceType) {
            SearchUtil.ResourceType.STRING -> STRING_RESOURCE_TYPE
            SearchUtil.ResourceType.PLURAL -> PLURAL_RESOURCE_TYPE
        }

        return receiverParts.takeLast(MIN_RESOURCE_REFERENCE_PARTS) == listOf(R_CLASS, expectedResourceType)
    }

    private companion object {
        private const val MIN_RESOURCE_REFERENCE_PARTS = 2
        private const val R_CLASS = "R"
        private const val STRING_RESOURCE_TYPE = "string"
        private const val PLURAL_RESOURCE_TYPE = "plurals"
    }
}

data class KotlinResourceReferenceReplacement(
    val fromKey: String,
    val toKey: String,
    val resourceType: SearchUtil.ResourceType,
)
