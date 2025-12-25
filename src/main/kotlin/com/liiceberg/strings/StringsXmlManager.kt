package com.liiceberg.strings

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.liiceberg.module.ModuleFileFinder

class StringsXmlManager(
    private val project: Project,
    private val module: Module,
    private val entries: Map<String, String>,
) {

    private val psiManager = PsiManager.getInstance(project)
    private val codeStyleManager = CodeStyleManager.getInstance(project)

//        TODO: add translation
    fun update() {
        getResourcesFiles().forEach { file ->
            val rootTag = file.rootTag
            if (rootTag?.name == RESOURCES_TAG) {
                entries.forEach { (key, value) ->
                    addOrUpdateString(rootTag, key, value)
                }
                reformatFile(file)
            }
        }
    }

    private fun addOrUpdateString(rootTag: XmlTag, key: String, value: String) {
        WriteCommandAction.runWriteCommandAction(project) {
            val existingTag = rootTag.findSubTags(STRING_TAG).firstOrNull {
                it.getAttributeValue(NAME_TAG_ATTRIBUTE) == key
            }

            if (existingTag == null) {
                val newTag = rootTag.createChildTag(STRING_TAG, rootTag.namespace, value, false)
                newTag.setAttribute(NAME_TAG_ATTRIBUTE, key)
                rootTag.addSubTag(newTag, false)
            }
        }
    }

    private fun reformatFile(psiFile: XmlFile) {
        codeStyleManager.reformat(psiFile)
    }

    private fun getResourcesFiles(): List<XmlFile> {
        return ModuleFileFinder.getModuleStringFiles(module).mapNotNull {
            psiManager.findFile(it) as? XmlFile
        }
    }

    companion object {
        private const val RESOURCES_TAG = "resources"
        const val STRING_TAG = "string"
        private const val NAME_TAG_ATTRIBUTE = "name"
    }
}