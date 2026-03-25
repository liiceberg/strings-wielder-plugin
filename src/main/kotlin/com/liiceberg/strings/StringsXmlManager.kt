package com.liiceberg.strings

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.liiceberg.model.PluralResource
import com.liiceberg.model.Resource
import com.liiceberg.model.StringResource
import com.liiceberg.module.ModuleFileFinder

class StringsXmlManager(
    private val project: Project,
    private val module: Module,
    private val entries: Map<String, Resource>,
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
//                reformatFile(file)
            }
        }
    }

    private fun addOrUpdateString(rootTag: XmlTag, key: String, value: Resource) {
        WriteCommandAction.runWriteCommandAction(project) {
            val existingTag = rootTag.findSubTags(STRING_TAG).firstOrNull {
                it.getAttributeValue(NAME_TAG_ATTRIBUTE) == key
            }
            if (value is PluralResource) {
                if (existingTag == null) {
                    val newTag = rootTag.createChildTag(PLURAL_TAG, rootTag.namespace, null, false)
                    newTag.setAttribute(NAME_TAG_ATTRIBUTE, key)

                    value.one?.let {
                        if (it.isNotEmpty()) {
                            val item = rootTag.createChildTag(ITEM_TAG, rootTag.namespace, it, false)
                            item.setAttribute(QUANTITY_TAG_ATTRIBUTE, "one")
                            newTag.addSubTag(item, false)
                        }
                    }
                    value.few?.let {
                        if (it.isNotEmpty()) {
                            val item = rootTag.createChildTag(ITEM_TAG, rootTag.namespace, it, false)
                            item.setAttribute(QUANTITY_TAG_ATTRIBUTE, "few")
                            newTag.addSubTag(item, false)
                        }
                    }
                    value.many?.let {
                        if (it.isNotEmpty()) {
                            val item = rootTag.createChildTag(ITEM_TAG, rootTag.namespace, it, false)
                            item.setAttribute(QUANTITY_TAG_ATTRIBUTE, "many")
                            newTag.addSubTag(item, false)
                        }
                    }
                    value.other?.let {
                        if (it.isNotEmpty()) {
                            val item = rootTag.createChildTag(ITEM_TAG, rootTag.namespace, it, false)
                            item.setAttribute(QUANTITY_TAG_ATTRIBUTE, "other")
                            newTag.addSubTag(item, false)
                        }
                    }
                    rootTag.addSubTag(newTag, false)
                }
            } else {
                value as StringResource
                if (existingTag == null) {
                    val newTag = rootTag.createChildTag(STRING_TAG, rootTag.namespace, value.value, false)
                    newTag.setAttribute(NAME_TAG_ATTRIBUTE, key)
                    rootTag.addSubTag(newTag, false)
                } else {
                    val tagValue = existingTag.value
                    tagValue.textElements.forEach { it.delete() }
                    tagValue.setText(value.value)
                }
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
        const val PLURAL_TAG = "plurals"
        const val ITEM_TAG = "item"
        const val QUANTITY_TAG_ATTRIBUTE = "quantity"
        const val NAME_TAG_ATTRIBUTE = "name"
    }
}