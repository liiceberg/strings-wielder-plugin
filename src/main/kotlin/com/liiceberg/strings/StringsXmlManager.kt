package com.liiceberg.strings

import com.android.tools.idea.ui.resourcemanager.importer.getOrCreateDefaultResDirectory
import com.intellij.openapi.application.ApplicationManager
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
import com.liiceberg.strings.translator.ResourceDirectoryLanguage
import com.liiceberg.strings.translator.SupportedAppLanguage
import com.liiceberg.strings.translator.Translator
import com.liiceberg.ui.entity.HardcodedStringEntity
import com.liiceberg.utils.Constants
import com.liiceberg.utils.getFacet
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.idea.core.util.toVirtualFile

class StringsXmlManager(
    private val project: Project,
    private val module: Module,
    private val entries: List<HardcodedStringEntity>,
) {

    private val psiManager = PsiManager.getInstance(project)
    private val codeStyleManager = CodeStyleManager.getInstance(project)
    private val translator = Translator()

    fun update() = runBlocking {
        getResourcesFiles().forEach { file ->
            val rootTag = file.rootTag ?: return@forEach
            if (rootTag.name != RESOURCES_TAG) return@forEach

            when (val directoryLanguage = resolveDirectoryLanguage(file)) {
                ResourceDirectoryLanguage.Default -> {
                    entries.forEach { entity ->
                        addOrUpdateString(rootTag, entity.key, entity.toResource())
                    }
                }
                is ResourceDirectoryLanguage.Known -> {
                    entries.forEach { entity ->
                        buildTranslatedResource(entity, directoryLanguage.language)?.let { resource ->
                            addOrUpdateString(rootTag, entity.key, resource)
                        }
                    }
                }
                ResourceDirectoryLanguage.Unsupported -> Unit
            }

            reformatFile(file)
        }
    }

    private suspend fun buildTranslatedResource(
        entity: HardcodedStringEntity,
        targetLanguage: SupportedAppLanguage,
    ): Resource? {
        val resource = entity.toResource()
        val sourceLanguage = entity.sourceLanguage ?: return null

        return if (sourceLanguage == targetLanguage) {
            resource
        } else {
            translator.translate(resource, sourceLanguage, targetLanguage)
        }
    }

    private fun addOrUpdateString(rootTag: XmlTag, key: String, value: Resource) {
        WriteCommandAction.runWriteCommandAction(project) {
            when (value) {
                is PluralResource -> addOrUpdatePlural(rootTag, key, value)
                is StringResource -> addOrUpdateText(rootTag, key, value)
            }
        }
    }

    private fun addOrUpdateText(rootTag: XmlTag, key: String, value: StringResource) {
        val existingTag = rootTag.findSubTags(STRING_TAG).firstOrNull {
            it.getAttributeValue(NAME_TAG_ATTRIBUTE) == key
        }

        if (existingTag == null) {
            val newTag = rootTag.createChildTag(STRING_TAG, rootTag.namespace, value.value, false)
            newTag.setAttribute(NAME_TAG_ATTRIBUTE, key)
            rootTag.addSubTag(newTag, false)
            return
        }

        val tagValue = existingTag.value
        tagValue.textElements.forEach { it.delete() }
        tagValue.setText(value.value)
    }

    private fun addOrUpdatePlural(rootTag: XmlTag, key: String, value: PluralResource) {
        val existingTag = rootTag.findSubTags(PLURAL_TAG).firstOrNull {
            it.getAttributeValue(NAME_TAG_ATTRIBUTE) == key
        } ?: rootTag.createChildTag(PLURAL_TAG, rootTag.namespace, null, false).also {
            it.setAttribute(NAME_TAG_ATTRIBUTE, key)
            rootTag.addSubTag(it, false)
        }

        updatePluralItem(rootTag, existingTag, QUANTITY_ONE, value.one)
        updatePluralItem(rootTag, existingTag, QUANTITY_FEW, value.few)
        updatePluralItem(rootTag, existingTag, QUANTITY_MANY, value.many)
        updatePluralItem(rootTag, existingTag, QUANTITY_OTHER, value.other)
    }

    private fun updatePluralItem(
        rootTag: XmlTag,
        pluralTag: XmlTag,
        quantity: String,
        value: String?,
    ) {
        val existingItem = pluralTag.findSubTags(ITEM_TAG).firstOrNull {
            it.getAttributeValue(QUANTITY_TAG_ATTRIBUTE) == quantity
        }

        if (value.isNullOrBlank()) {
            existingItem?.delete()
            return
        }

        if (existingItem == null) {
            val item = rootTag.createChildTag(ITEM_TAG, rootTag.namespace, value, false)
            item.setAttribute(QUANTITY_TAG_ATTRIBUTE, quantity)
            pluralTag.addSubTag(item, false)
            return
        }

        val tagValue = existingItem.value
        tagValue.textElements.forEach { it.delete() }
        tagValue.setText(value)
    }

    private fun reformatFile(psiFile: XmlFile) {
        WriteCommandAction.runWriteCommandAction(project) {
            codeStyleManager.reformat(psiFile)
        }
    }

    private fun getResourcesFiles(): List<XmlFile> {
        val existingFiles = ApplicationManager.getApplication().runReadAction<List<XmlFile>> {
            ModuleFileFinder.getModuleStringFiles(module).mapNotNull {
                psiManager.findFile(it) as? XmlFile
            }
        }.toMutableList()

        if (existingFiles.any { it.virtualFile.parent?.name == DEFAULT_VALUES_DIRECTORY }) {
            return existingFiles
        }

        ensureDefaultStringsFile()?.let(existingFiles::add)
        return existingFiles
    }

    private fun ensureDefaultStringsFile(): XmlFile? {
        val facet = module.getFacet() ?: return null
        val resDirectory = getOrCreateDefaultResDirectory(facet).toVirtualFile() ?: return null

        return WriteCommandAction.writeCommandAction(project).compute<XmlFile?, Throwable> {
            val valuesDirectory = resDirectory.findChild(DEFAULT_VALUES_DIRECTORY)
                ?: resDirectory.createChildDirectory(this, DEFAULT_VALUES_DIRECTORY)
            val stringsFile = valuesDirectory.findChild(Constants.STRING_RESOURCE_FILE)
                ?: valuesDirectory.createChildData(this, Constants.STRING_RESOURCE_FILE).also { file ->
                    file.setBinaryContent(DEFAULT_STRINGS_XML.toByteArray(Charsets.UTF_8))
                }
            psiManager.findFile(stringsFile) as? XmlFile
        }
    }

    private fun resolveDirectoryLanguage(file: XmlFile): ResourceDirectoryLanguage {
        val directoryName = file.virtualFile.parent?.name ?: return ResourceDirectoryLanguage.Unsupported
        return SupportedAppLanguage.resolveResourceDirectory(directoryName)
    }

    private fun HardcodedStringEntity.toResource(): Resource {
        return pluralForm ?: StringResource(value)
    }

    companion object {
        private const val RESOURCES_TAG = "resources"
        const val STRING_TAG = "string"
        const val PLURAL_TAG = "plurals"
        const val ITEM_TAG = "item"
        const val QUANTITY_TAG_ATTRIBUTE = "quantity"
        const val NAME_TAG_ATTRIBUTE = "name"
        private const val DEFAULT_VALUES_DIRECTORY = "values"
        private const val QUANTITY_ONE = "one"
        private const val QUANTITY_FEW = "few"
        private const val QUANTITY_MANY = "many"
        private const val QUANTITY_OTHER = "other"
        private const val DEFAULT_STRINGS_XML = "<resources>\n</resources>\n"
    }
}
