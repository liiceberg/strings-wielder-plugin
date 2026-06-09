package com.liiceberg.strings

import com.android.tools.idea.ui.resourcemanager.importer.getOrCreateDefaultResDirectory
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.liiceberg.model.PluralResource
import com.liiceberg.model.Resource
import com.liiceberg.model.StringResource
import com.liiceberg.module.ModuleFileFinder
import com.liiceberg.strings.SearchUtil.ResourceType
import com.liiceberg.strings.translator.ResourceDirectoryLanguage
import com.liiceberg.strings.translator.SupportedAppLanguage
import com.liiceberg.strings.translator.Translator
import com.liiceberg.ui.entity.HardcodedStringEntity
import com.liiceberg.utils.Constants
import com.liiceberg.utils.getFacet
import org.jetbrains.kotlin.idea.core.util.toVirtualFile

class StringsXmlManager(
    private val project: Project,
    private val module: Module,
    private val entries: List<HardcodedStringEntity>,
    private val baseLanguage: SupportedAppLanguage,
) {

    private val psiManager = PsiManager.getInstance(project)
    private val codeStyleManager = CodeStyleManager.getInstance(project)
    private val translator = Translator()

    suspend fun update(onTranslationStarted: (String) -> Unit = {}) {
        getResourcesFiles().forEach { file ->
            ProgressManager.checkCanceled()
            if (!isResourcesFile(file)) return@forEach

            when (val directoryLanguage = resolveDirectoryLanguage(file)) {
                ResourceDirectoryLanguage.Default -> {
                    entries.forEach { entity ->
                        ProgressManager.checkCanceled()
                        buildBaseResource(entity, onTranslationStarted).let { prepared ->
                            addOrUpdateString(file, entity.key, prepared.resource, prepared.translatable)
                        }
                    }
                }
                is ResourceDirectoryLanguage.Known -> {
                    entries.forEach { entity ->
                        ProgressManager.checkCanceled()
                        buildLocalizedResource(entity, directoryLanguage.language, onTranslationStarted)?.let { resource ->
                            addOrUpdateString(file, entity.key, resource)
                        }
                    }
                }
                ResourceDirectoryLanguage.Unsupported -> Unit
            }

            reformatFile(file)
        }
    }

    private suspend fun buildBaseResource(
        entity: HardcodedStringEntity,
        onTranslationStarted: (String) -> Unit,
    ): PreparedResource {
        val resource = entity.toResource()
        val sourceLanguage = entity.sourceLanguage

        return when {
            sourceLanguage == SupportedAppLanguage.NON_TRANSLATABLE -> PreparedResource(
                resource = resource,
                translatable = false,
            )
            sourceLanguage == null || sourceLanguage == baseLanguage -> PreparedResource(resource)
            !sourceLanguage.isTranslatable -> PreparedResource(
                resource = resource,
                translatable = false,
            )
            else -> {
                onTranslationStarted("${entity.key} -> ${baseLanguage.displayName}")
                PreparedResource(
                    resource = runCatching {
                        translator.translate(resource, sourceLanguage, baseLanguage)
                    }.getOrElse {
                        resource
                    }
                )
            }
        }
    }

    private suspend fun buildLocalizedResource(
        entity: HardcodedStringEntity,
        targetLanguage: SupportedAppLanguage,
        onTranslationStarted: (String) -> Unit,
    ): Resource? {
        val resource = entity.toResource()
        val sourceLanguage = entity.sourceLanguage ?: return null

        if (!sourceLanguage.isTranslatable || sourceLanguage == SupportedAppLanguage.NON_TRANSLATABLE) {
            return null
        }

        return if (sourceLanguage == targetLanguage) {
            resource
        } else {
            onTranslationStarted("${entity.key} -> ${targetLanguage.displayName}")
            runCatching {
                translator.translate(resource, sourceLanguage, targetLanguage)
            }.getOrNull()
        }
    }

    private fun addOrUpdateString(
        file: XmlFile,
        key: String,
        value: Resource,
        translatable: Boolean = true,
    ) {
        WriteCommandAction.runWriteCommandAction(project) {
            val rootTag = file.rootTag ?: return@runWriteCommandAction
            if (rootTag.name != RESOURCES_TAG) return@runWriteCommandAction

            when (value) {
                is PluralResource -> addOrUpdatePlural(rootTag, key, value, translatable)
                is StringResource -> addOrUpdateText(rootTag, key, value, translatable)
                else -> Unit
            }
        }
    }

    private fun addOrUpdateText(
        rootTag: XmlTag,
        key: String,
        value: StringResource,
        translatable: Boolean,
    ) {
        val existingTag = rootTag.findSubTags(STRING_TAG).firstOrNull {
            it.getAttributeValue(NAME_TAG_ATTRIBUTE) == key
        }

        if (existingTag == null) {
            val newTag = rootTag.createChildTag(STRING_TAG, rootTag.namespace, value.value, false)
            newTag.setAttribute(NAME_TAG_ATTRIBUTE, key)
            applyTranslatableAttribute(newTag, translatable)
            rootTag.addSubTag(newTag, false)
            return
        }

        applyTranslatableAttribute(existingTag, translatable)
        val tagValue = existingTag.value
        tagValue.textElements.forEach { it.delete() }
        tagValue.text = value.value
    }

    private fun addOrUpdatePlural(
        rootTag: XmlTag,
        key: String,
        value: PluralResource,
        translatable: Boolean,
    ) {
        val existingTag = rootTag.findSubTags(PLURAL_TAG).firstOrNull {
            it.getAttributeValue(NAME_TAG_ATTRIBUTE) == key
        } ?: run {
            val newTag = rootTag.createChildTag(PLURAL_TAG, rootTag.namespace, null, false)
            newTag.setAttribute(NAME_TAG_ATTRIBUTE, key)
            rootTag.addSubTag(newTag, false)
        }

        applyTranslatableAttribute(existingTag, translatable)
        updatePluralItem(rootTag, existingTag, QUANTITY_ZERO, value.zero)
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
        tagValue.text = value
    }

    private fun applyTranslatableAttribute(tag: XmlTag, translatable: Boolean) {
        if (translatable) {
            tag.getAttribute(TRANSLATABLE_TAG_ATTRIBUTE)?.delete()
        } else {
            tag.setAttribute(TRANSLATABLE_TAG_ATTRIBUTE, FALSE_ATTRIBUTE_VALUE)
        }
    }

    private fun reformatFile(psiFile: XmlFile) {
        WriteCommandAction.runWriteCommandAction(project) {
            codeStyleManager.reformat(psiFile)
        }
    }

    fun deleteResources(resources: List<ResourceDeletionRequest>): Int {
        var deletedCount = 0
        getResourcesFiles().forEach { file ->
            ProgressManager.checkCanceled()
            if (!isResourcesFile(file)) return@forEach

            val deletedInFile = deleteResourcesFromFile(file, resources)
            if (deletedInFile > 0) {
                deletedCount += deletedInFile
                reformatFile(file)
            }
        }
        return deletedCount
    }

    private fun deleteResourcesFromFile(
        file: XmlFile,
        resources: List<ResourceDeletionRequest>,
    ): Int {
        var deletedCount = 0
        WriteCommandAction.runWriteCommandAction(project) {
            val rootTag = file.rootTag ?: return@runWriteCommandAction
            resources.forEach { resource ->
                val tagName = when (resource.resourceType) {
                    ResourceType.STRING -> STRING_TAG
                    ResourceType.PLURAL -> PLURAL_TAG
                }
                val tag = rootTag.findSubTags(tagName).firstOrNull {
                    it.getAttributeValue(NAME_TAG_ATTRIBUTE) == resource.key
                } ?: return@forEach
                tag.delete()
                deletedCount += 1
            }
        }
        return deletedCount
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
        return ApplicationManager.getApplication().runReadAction<ResourceDirectoryLanguage> {
            val directoryName = file.virtualFile.parent?.name ?: return@runReadAction ResourceDirectoryLanguage.Unsupported
            SupportedAppLanguage.resolveResourceDirectory(directoryName)
        }
    }

    private fun isResourcesFile(file: XmlFile): Boolean {
        return ApplicationManager.getApplication().runReadAction<Boolean> {
            file.rootTag?.name == RESOURCES_TAG
        }
    }

    private fun HardcodedStringEntity.toResource(): Resource {
        return pluralForm ?: StringResource(value)
    }

    private data class PreparedResource(
        val resource: Resource,
        val translatable: Boolean = true,
    )

    data class ResourceDeletionRequest(
        val key: String,
        val resourceType: ResourceType,
    )

    companion object {
        private const val RESOURCES_TAG = "resources"
        const val STRING_TAG = "string"
        const val PLURAL_TAG = "plurals"
        const val ITEM_TAG = "item"
        const val QUANTITY_TAG_ATTRIBUTE = "quantity"
        const val NAME_TAG_ATTRIBUTE = "name"
        private const val TRANSLATABLE_TAG_ATTRIBUTE = "translatable"
        private const val FALSE_ATTRIBUTE_VALUE = "false"
        private const val DEFAULT_VALUES_DIRECTORY = "values"
        const val QUANTITY_ZERO = "zero"
        const val QUANTITY_ONE = "one"
        const val QUANTITY_FEW = "few"
        const val QUANTITY_MANY = "many"
        const val QUANTITY_OTHER = "other"
        private const val DEFAULT_STRINGS_XML = "<resources>\n</resources>\n"
    }
}
