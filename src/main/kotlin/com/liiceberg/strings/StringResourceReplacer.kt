package com.liiceberg.strings

import ai.grazie.text.TextRange
import ai.grazie.text.replace
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.liiceberg.strings.detector.Pattern
import com.liiceberg.strings.detector.PatternType
import com.liiceberg.ui.entity.HardcodedStringEntity
import com.liiceberg.utils.Constants
import com.liiceberg.utils.LocalStorage
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.resolve.ImportPath

class StringResourceReplacer(private val project: Project, private val entries: List<HardcodedStringEntity>) {

    private val psiManager = PsiManager.getInstance(project)
    private val ktPsiFactory = KtPsiFactory(project)

    suspend fun replace(onTranslationStarted: (String) -> Unit = {}) {
        entries.filter { it.isSelected }.groupBy { it.virtualFile }.forEach { (file, entityList) ->

            val importsList = mutableListOf<String>()
            getImportR()?.let { importsList.add(it) }
            if (entityList.any { it.pluralForm != null }) {
                importsList.add(PLURAL_RESOURCE_IMPORT)
            }
            if (!entityList.all { it.pluralForm != null }) {
                importsList.add(STRING_RESOURCE_IMPORT)
            }

            val psiFile = ApplicationManager.getApplication().runReadAction<KtFile?> {
                psiManager.findFile(file) as? KtFile
            }
            psiFile?.let {
                addImports(importsList, it)
                val stringsToReplace = buildMap {
                    entityList.forEach { entity ->
                        put(entity.value, entity)
                    }
                }
                replaceInKotlinFile(stringsToReplace, it)
            }
        }
        updateStringXMLFile(onTranslationStarted)
    }

    private fun replaceInKotlinFile(stringsToReplace: Map<String, HardcodedStringEntity>, ktFile: KtFile) {
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

    @Suppress("UnstableApiUsage")
    private fun addImports(imports: List<String>, ktFile: KtFile) {
        WriteCommandAction.runWriteCommandAction(project) {
            val existingImports = ktFile.importDirectives.map { it.text.split(' ').last() }
            imports.forEach { currentImport ->
                if (currentImport !in existingImports) {
                    val newImport = ktPsiFactory.createImportDirective(
                        ImportPath.fromString(currentImport)
                    )
                    val importList = ktFile.importList
                    if (importList != null) {
                        importList.addAfter(newImport, importList.lastChild)
                    } else {
                        val packageDirective = ktFile.packageDirective
                        if (packageDirective != null) {
                            ktFile.addAfter(newImport, packageDirective)
                            ktFile.addAfter(ktPsiFactory.createNewLine(), packageDirective)
                        } else {
                            ktFile.addAfter(newImport, null)
                        }
                    }
                }
            }
        }
    }

    private fun getImportR(): String? {
        return LocalStorage.getData(Constants.Preferences.IMPORT_PACKAGE)?.let {
                RESOURCES_IMPORT_TEMPLATE.format(it)
            }

    }

    private suspend fun updateStringXMLFile(onTranslationStarted: (String) -> Unit) {
        entries.filter { it.isSelected }.groupBy { it.module }.forEach { (module, entityList) ->
            val preparedEntities = entityList.map { entity ->
                if (entity.pluralForm != null) {
                    entity
                } else {
                    entity.copy(value = buildResourceValue(entity))
                }
            }
            StringsXmlManager(project, module, preparedEntities).update(onTranslationStarted)
        }
    }

    private fun buildResourceAccess(entity: HardcodedStringEntity): String {
        if (entity.pluralForm != null) {
            val number = entity.pluralForm?.currentNumber ?: 1
            return PLURAL_RESOURCE_TEMPLATE.format(entity.key, number)
        }

        val templateArgs = buildTemplateArguments(entity)
        return if (templateArgs.isEmpty()) {
            STRING_RESOURCE_TEMPLATE.format(entity.key)
        } else {
            STRING_RESOURCE_WITH_ARGS_TEMPLATE.format(entity.key, templateArgs.joinToString(", "))
        }
    }

    private fun buildResourceValue(entity: HardcodedStringEntity): String {
        val templatePatterns = entity.patterns
            .filter { it.type == PatternType.TEMPLATE }
            .sortedByDescending { it.range.first }

        var result = entity.value
        templatePatterns.forEach { pattern ->
            val replacement = pattern.templateFormat ?: pattern.value
            result = result.replace(
                TextRange(pattern.range.first, pattern.range.last + 1),
                replacement
            )
        }
        return result
    }

    private fun buildTemplateArguments(entity: HardcodedStringEntity): List<String> {
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
        const val STRING_RESOURCE_TEMPLATE = "stringResource(R.string.%s)"
        const val STRING_RESOURCE_WITH_ARGS_TEMPLATE = "stringResource(R.string.%s, %s)"
        const val PLURAL_RESOURCE_TEMPLATE = "pluralStringResource(R.plurals.%s, %d)"
        const val STRING_RESOURCE_IMPORT = "androidx.compose.ui.res.stringResource"
        const val PLURAL_RESOURCE_IMPORT = "androidx.compose.ui.res.pluralStringResource"
        const val RESOURCES_IMPORT_TEMPLATE = "%s.R"
    }
}
