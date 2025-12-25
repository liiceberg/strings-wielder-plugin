package com.liiceberg.strings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
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

    fun replace() {
        ApplicationManager.getApplication().runReadAction {
            val importsList = getRequiredImports()
            entries.filter { it.isSelected }.groupBy { it.virtualFile }.forEach { (file, entityList) ->
                psiManager.findFile(file)?.let { psiFile ->
                    psiFile as KtFile
                    addImports(importsList, psiFile)
                    val stringsToReplace = buildMap { entityList.forEach { put(it.value, it.key) } }
                    replaceInKotlinFile(stringsToReplace,psiFile)
                }
            }
            updateStringXMLFile()
        }
    }

    private fun replaceInKotlinFile(stringsToReplace: Map<String, String>, ktFile: KtFile) {
        WriteCommandAction.runWriteCommandAction(project) {
            ktFile.accept(object : KtTreeVisitorVoid() {
                override fun visitStringTemplateExpression(expression: KtStringTemplateExpression) {
                    super.visitStringTemplateExpression(expression)

                    val text = expression.text.trim('"')
                    if (stringsToReplace.containsKey(text)) {
                        val resourceAccess = STRING_RESOURCE_TEMPLATE.format(stringsToReplace[text])
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

    private fun getRequiredImports(): List<String> {
        return buildList {
            add(STRING_RESOURCE_IMPORT)
            LocalStorage.getData(Constants.Preferences.IMPORT_PACKAGE)?.let {
                add(RESOURCES_IMPORT_TEMPLATE.format(it))
            }
        }
    }

    private fun updateStringXMLFile() {
        entries.filter { it.isSelected }.groupBy { it.module }.forEach { (module, entityList) ->
            val newStringResources = buildMap { entityList.forEach { put(it.key, it.value) } }
            StringsXmlManager(project, module, newStringResources).update()
        }
    }

    private companion object {
        const val STRING_RESOURCE_TEMPLATE = "stringResource(R.string.%s)"
        const val STRING_RESOURCE_IMPORT = "androidx.compose.ui.res.stringResource"
        const val RESOURCES_IMPORT_TEMPLATE = "%s.R"
    }
}

