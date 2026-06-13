package com.liiceberg.strings

import ai.grazie.text.TextRange
import ai.grazie.text.replace
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.liiceberg.model.StringEntity
import com.liiceberg.strings.SearchUtil.ResourceType
import com.liiceberg.strings.detector.Pattern
import com.liiceberg.strings.detector.PatternType
import com.liiceberg.strings.translator.SupportedAppLanguage
import com.liiceberg.utils.addImports
import com.liiceberg.utils.getAndroidPackageName
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid

class StringResourceReplacer(
    private val project: Project,
    private val entries: List<StringEntity>,
    private val baseLanguage: SupportedAppLanguage,
) {

    private val psiManager = PsiManager.getInstance(project)
    private val ktPsiFactory = KtPsiFactory(project)
    private val modulePackages = mutableMapOf<String, String?>()

    suspend fun replace(onTranslationStarted: (String) -> Unit = {}) {
        entries.filter { it.isSelected }.groupBy { it.virtualFile }.forEach { (file, entityList) ->

            val importsList = mutableListOf<String>()
            if (entityList.any { shouldUsePluralAccess(it) }) {
                importsList.add(PLURAL_RESOURCE_IMPORT)
            }
            if (entityList.any { !shouldUsePluralAccess(it) }) {
                importsList.add(STRING_RESOURCE_IMPORT)
            }

            val psiFile = ApplicationManager.getApplication().runReadAction<KtFile?> {
                psiManager.findFile(file) as? KtFile
            }
            psiFile?.let {
                it.addImports(importsList, ktPsiFactory)
                val stringsToReplace = buildMap {
                    entityList.forEach { entity ->
                        put(entity.sourceValue, entity)
                    }
                }
                replaceInKotlinFile(stringsToReplace, it)
            }
        }
        updateStringXMLFile(onTranslationStarted)
    }

    private fun replaceInKotlinFile(stringsToReplace: Map<String, StringEntity>, ktFile: KtFile) {
        WriteCommandAction.runWriteCommandAction(project) {
            ktFile.accept(object : KtTreeVisitorVoid() {
                override fun visitStringTemplateExpression(expression: KtStringTemplateExpression) {
                    super.visitStringTemplateExpression(expression)

                    val text = expression.text.trim('"')
                    if (stringsToReplace.containsKey(text)) {
                        val entity = stringsToReplace.getValue(text)
                        val resourceAccess = buildResourceAccess(entity)
                        expression.replace(
                            ktPsiFactory.createExpression(resourceAccess)
                        )
                    }
                }
            })
        }
    }

    private suspend fun updateStringXMLFile(onTranslationStarted: (String) -> Unit) {
        entries
            .filter { it.isSelected && it.existingResource == null }
            .groupBy { it.module }
            .forEach { (module, entityList) ->
            val preparedEntities = entityList.map { entity ->
                if (entity.pluralForm != null) {
                    entity
                } else {
                    entity.copy(value = resolveFinalResourceValue(entity))
                }
            }
            StringsXmlManager(project, module, preparedEntities, baseLanguage).update(onTranslationStarted)
        }
    }

    private fun buildResourceAccess(entity: StringEntity): String {
        val resourceReference = resolveResourceReference(entity)
        if (resourceReference.resourceType == ResourceType.PLURAL) {
            val number = entity.pluralForm?.currentNumber ?: 1
            return PLURAL_RESOURCE_TEMPLATE.format(resourceReference.rReference, resourceReference.key, number)
        }

        val templateArgs = buildTemplateArguments(entity)
        return if (templateArgs.isEmpty()) {
            STRING_RESOURCE_TEMPLATE.format(resourceReference.rReference, resourceReference.key)
        } else {
            STRING_RESOURCE_WITH_ARGS_TEMPLATE.format(
                resourceReference.rReference,
                resourceReference.key,
                templateArgs.joinToString(", ")
            )
        }
    }

    private fun shouldUsePluralAccess(entity: StringEntity): Boolean {
        return resolveResourceReference(entity).resourceType == ResourceType.PLURAL
    }

    private fun resolveResourceReference(entity: StringEntity): ResourceReference {
        entity.existingResource?.let { existing ->
            return ResourceReference(
                key = existing.key,
                rReference = buildRReference(existing.packageName),
                resourceType = existing.resourceType,
            )
        }

        return ResourceReference(
            key = entity.key,
            rReference = buildRReference(getModulePackageName(entity.module)),
            resourceType = if (entity.pluralForm != null) ResourceType.PLURAL else ResourceType.STRING,
        )
    }

    private fun buildRReference(packageName: String?): String {
        return if (packageName.isNullOrBlank()) {
            DEFAULT_R_REFERENCE
        } else {
            "$packageName.$DEFAULT_R_REFERENCE"
        }
    }

    private fun getModulePackageName(module: Module): String? {
        return modulePackages.getOrPut(module.name) {
            ApplicationManager.getApplication().runReadAction<String?> {
                module.getAndroidPackageName()
            }
        }
    }

    private fun buildResourceValue(entity: StringEntity): String {
        val templatePatterns = entity.patterns
            .filter { it.type == PatternType.TEMPLATE }
            .sortedByDescending { it.range.first }

        var result = entity.sourceValue
        templatePatterns.forEach { pattern ->
            val replacement = pattern.templateFormat ?: pattern.value
            result = result.replace(
                TextRange(pattern.range.first, pattern.range.last + 1),
                replacement
            )
        }
        return result
    }

    private fun resolveFinalResourceValue(entity: StringEntity): String {
        return if (entity.value != entity.sourceValue) {
            entity.value
        } else {
            buildResourceValue(entity)
        }
    }

    private fun buildTemplateArguments(entity: StringEntity): List<String> {
        return entity.patterns
            .filter { it.type == PatternType.TEMPLATE }
            .sortedBy { it.range.first }
            .mapNotNull { pattern ->
                if (pattern.templateFormat == null) {
                    return@mapNotNull null
                }
                pattern.extractArgumentExpression()
            }
    }

    private fun Pattern.extractArgumentExpression(): String? {
        return when {
            value.startsWith("\${") && value.endsWith("}") -> value.removePrefix("\${").removeSuffix("}")
            value.startsWith("$") && value.length > 1 -> value.removePrefix("$")
            else -> null
        }
    }

    private companion object {
        const val STRING_RESOURCE_TEMPLATE = "stringResource(%s.string.%s)"
        const val STRING_RESOURCE_WITH_ARGS_TEMPLATE = "stringResource(%s.string.%s, %s)"
        const val PLURAL_RESOURCE_TEMPLATE = "pluralStringResource(%s.plurals.%s, %d)"
        const val STRING_RESOURCE_IMPORT = "androidx.compose.ui.res.stringResource"
        const val PLURAL_RESOURCE_IMPORT = "androidx.compose.ui.res.pluralStringResource"
        const val DEFAULT_R_REFERENCE = "R"
    }

    private data class ResourceReference(
        val key: String,
        val rReference: String,
        val resourceType: ResourceType,
    )
}
