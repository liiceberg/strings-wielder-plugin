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
import com.liiceberg.module.ModuleExplorer
import com.liiceberg.module.ModuleFileFinder
import com.liiceberg.strings.translator.ResourceDirectoryLanguage
import com.liiceberg.strings.translator.SupportedAppLanguage
import com.liiceberg.strings.translator.Translator
import com.liiceberg.utils.Constants
import com.liiceberg.utils.LocalStorage
import com.liiceberg.utils.getFacet
import org.jetbrains.kotlin.idea.core.util.toVirtualFile

class ExistingResourcesTranslationService(
    private val project: Project,
) {

    enum class Mode {
        EXISTING_MODULE_LOCALES,
        ALL_APP_LOCALES,
    }

    private val moduleExplorer = ModuleExplorer(project)
    private val psiManager = PsiManager.getInstance(project)
    private val codeStyleManager = CodeStyleManager.getInstance(project)
    private val translator = Translator()

    suspend fun translateMissingResources(
        mode: Mode = Mode.EXISTING_MODULE_LOCALES,
        onTranslationStarted: (String) -> Unit = {},
    ) {
        val baseLanguage = getSavedBaseLanguage()
        val modules = ApplicationManager.getApplication().runReadAction<List<Module>> {
            moduleExplorer.getAndroidModules()
        }
        val appLocales = if (mode == Mode.ALL_APP_LOCALES) {
            collectAppLocales(modules)
        } else {
            emptySet()
        }

        modules.forEach { module ->
            ProgressManager.checkCanceled()
            translateModuleResources(module, baseLanguage, mode, appLocales, onTranslationStarted)
        }
    }

    private suspend fun translateModuleResources(
        module: Module,
        baseLanguage: SupportedAppLanguage,
        mode: Mode,
        appLocales: Set<SupportedAppLanguage>,
        onTranslationStarted: (String) -> Unit,
    ) {
        val resourceFiles = when (mode) {
            Mode.EXISTING_MODULE_LOCALES -> getModuleResourceFiles(module)
            Mode.ALL_APP_LOCALES -> ensureModuleResourceFiles(module, appLocales)
        }
        val baseFile = resourceFiles.firstOrNull { it.directoryLanguage == ResourceDirectoryLanguage.Default }?.file ?: return
        val baseEntries = readBaseEntries(baseFile)
        if (baseEntries.isEmpty()) return

        resourceFiles
            .filter { it.directoryLanguage is ResourceDirectoryLanguage.Known }
            .forEach { localizedFile ->
                ProgressManager.checkCanceled()
                val targetLanguage = (localizedFile.directoryLanguage as? ResourceDirectoryLanguage.Known)?.language ?: return@forEach
                syncLocalizedFile(
                    file = localizedFile.file,
                    targetLanguage = targetLanguage,
                    baseEntries = baseEntries,
                    baseLanguage = baseLanguage,
                    onTranslationStarted = onTranslationStarted,
                )
            }
    }

    private fun collectAppLocales(modules: List<Module>): Set<SupportedAppLanguage> {
        return modules
            .flatMap { module ->
                getModuleResourceFiles(module).mapNotNull { localizedFile ->
                    (localizedFile.directoryLanguage as? ResourceDirectoryLanguage.Known)?.language
                }
            }
            .toSet()
    }

    private fun getModuleResourceFiles(module: Module): List<LocalizedXmlFile> {
        return ApplicationManager.getApplication().runReadAction<List<LocalizedXmlFile>> {
            ModuleFileFinder.getModuleStringFiles(module)
                .distinctBy { it.path }
                .mapNotNull { virtualFile ->
                    val xmlFile = psiManager.findFile(virtualFile) as? XmlFile ?: return@mapNotNull null
                    val directoryLanguage = resolveDirectoryLanguage(xmlFile)
                    if (directoryLanguage == ResourceDirectoryLanguage.Unsupported) {
                        return@mapNotNull null
                    }
                    LocalizedXmlFile(xmlFile, directoryLanguage)
                }
        }
    }

    private fun ensureModuleResourceFiles(
        module: Module,
        appLocales: Set<SupportedAppLanguage>,
    ): List<LocalizedXmlFile> {
        val existingFiles = getModuleResourceFiles(module).toMutableList()
        val existingLanguages = existingFiles.mapNotNull { localizedFile ->
            (localizedFile.directoryLanguage as? ResourceDirectoryLanguage.Known)?.language
        }.toSet()

        appLocales
            .filter { it.isTranslatable && it !in existingLanguages }
            .forEach { language ->
                ensureLocalizedStringsFile(module, language)?.let { file ->
                    existingFiles += LocalizedXmlFile(
                        file = file,
                        directoryLanguage = ResourceDirectoryLanguage.Known(language),
                    )
                }
            }

        return existingFiles
    }

    private fun ensureLocalizedStringsFile(
        module: Module,
        language: SupportedAppLanguage,
    ): XmlFile? {
        val facet = module.getFacet() ?: return null
        val resDirectory = getOrCreateDefaultResDirectory(facet).toVirtualFile() ?: return null

        return WriteCommandAction.writeCommandAction(project).compute<XmlFile?, Throwable> {
            val directory = resDirectory.findChild(language.androidValuesDirectoryName)
                ?: resDirectory.createChildDirectory(this, language.androidValuesDirectoryName)
            val stringsFile = directory.findChild(Constants.STRING_RESOURCE_FILE)
                ?: directory.createChildData(this, Constants.STRING_RESOURCE_FILE).also { file ->
                    file.setBinaryContent(DEFAULT_STRINGS_XML.toByteArray(Charsets.UTF_8))
                }
            psiManager.findFile(stringsFile) as? XmlFile
        }
    }

    private fun readBaseEntries(file: XmlFile): List<BaseResourceEntry> {
        return ApplicationManager.getApplication().runReadAction<List<BaseResourceEntry>> {
            val rootTag = file.rootTag ?: return@runReadAction emptyList()
            if (rootTag.name != RESOURCES_TAG) {
                return@runReadAction emptyList()
            }

            rootTag.subTags.mapNotNull { tag ->
                val key = tag.getAttributeValue(StringsXmlManager.NAME_TAG_ATTRIBUTE) ?: return@mapNotNull null
                if (tag.getAttributeValue(TRANSLATABLE_ATTRIBUTE) == FALSE_VALUE) {
                    return@mapNotNull null
                }

                when (tag.name) {
                    StringsXmlManager.STRING_TAG -> {
                        val value = tag.value.trimmedText.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                        BaseResourceEntry(key, StringResource(value))
                    }

                    StringsXmlManager.PLURAL_TAG -> {
                        val plural = readPluralResource(tag) ?: return@mapNotNull null
                        BaseResourceEntry(key, plural)
                    }

                    else -> null
                }
            }
        }
    }

    private suspend fun syncLocalizedFile(
        file: XmlFile,
        targetLanguage: SupportedAppLanguage,
        baseEntries: List<BaseResourceEntry>,
        baseLanguage: SupportedAppLanguage,
        onTranslationStarted: (String) -> Unit,
    ) {
        val existingState = readLocalizedState(file)

        baseEntries.forEach { entry ->
            ProgressManager.checkCanceled()

            when (entry.resource) {
                is StringResource -> {
                    if (existingState.strings.contains(entry.key)) return@forEach

                    val translated = translateResource(entry.resource, baseLanguage, targetLanguage, entry.key, onTranslationStarted)
                    addMissingResource(file, entry.key, translated)
                    existingState.strings += entry.key
                }

                is PluralResource -> {
                    val translated = translateResource(entry.resource, baseLanguage, targetLanguage, entry.key, onTranslationStarted)
                    val existingQuantities = existingState.plurals[entry.key].orEmpty()
                    if (hasAllPluralQuantities(translated, existingQuantities)) return@forEach

                    addMissingResource(file, entry.key, translated)
                    existingState.plurals[entry.key] = requiredPluralQuantities(translated)
                }

                else -> Unit
            }
        }

        reformatFile(file)
    }

    private suspend fun translateResource(
        resource: Resource,
        baseLanguage: SupportedAppLanguage,
        targetLanguage: SupportedAppLanguage,
        key: String,
        onTranslationStarted: (String) -> Unit,
    ): Resource {
        if (baseLanguage == targetLanguage) {
            return resource
        }

        onTranslationStarted("$key -> ${targetLanguage.displayName}")
        return translator.translate(resource, baseLanguage, targetLanguage)
    }

    private fun addMissingResource(
        file: XmlFile,
        key: String,
        resource: Resource,
    ) {
        WriteCommandAction.runWriteCommandAction(project) {
            val rootTag = file.rootTag ?: return@runWriteCommandAction
            if (rootTag.name != RESOURCES_TAG) return@runWriteCommandAction

            when (resource) {
                is StringResource -> addMissingString(rootTag, key, resource)
                is PluralResource -> addMissingPlural(rootTag, key, resource)
                else -> Unit
            }
        }
    }

    private fun addMissingString(
        rootTag: XmlTag,
        key: String,
        value: StringResource,
    ) {
        val existingTag = rootTag.findSubTags(StringsXmlManager.STRING_TAG).firstOrNull {
            it.getAttributeValue(StringsXmlManager.NAME_TAG_ATTRIBUTE) == key
        }
        if (existingTag != null) return

        val newTag = rootTag.createChildTag(StringsXmlManager.STRING_TAG, rootTag.namespace, value.value, false)
        newTag.setAttribute(StringsXmlManager.NAME_TAG_ATTRIBUTE, key)
        rootTag.addSubTag(newTag, false)
    }

    private fun addMissingPlural(
        rootTag: XmlTag,
        key: String,
        value: PluralResource,
    ) {
        val pluralTag = rootTag.findSubTags(StringsXmlManager.PLURAL_TAG).firstOrNull {
            it.getAttributeValue(StringsXmlManager.NAME_TAG_ATTRIBUTE) == key
        } ?: rootTag.createChildTag(StringsXmlManager.PLURAL_TAG, rootTag.namespace, null, false).also {
            it.setAttribute(StringsXmlManager.NAME_TAG_ATTRIBUTE, key)
            rootTag.addSubTag(it, false)
        }

        addMissingPluralItem(rootTag, pluralTag, StringsXmlManager.QUANTITY_ZERO, value.zero)
        addMissingPluralItem(rootTag, pluralTag, StringsXmlManager.QUANTITY_ONE, value.one)
        addMissingPluralItem(rootTag, pluralTag, StringsXmlManager.QUANTITY_FEW, value.few)
        addMissingPluralItem(rootTag, pluralTag, StringsXmlManager.QUANTITY_MANY, value.many)
        addMissingPluralItem(rootTag, pluralTag, StringsXmlManager.QUANTITY_OTHER, value.other)
    }

    private fun addMissingPluralItem(
        rootTag: XmlTag,
        pluralTag: XmlTag,
        quantity: String,
        value: String?,
    ) {
        val itemValue = value?.takeIf { it.isNotBlank() } ?: return
        val existingItem = pluralTag.findSubTags(StringsXmlManager.ITEM_TAG).firstOrNull {
            it.getAttributeValue(StringsXmlManager.QUANTITY_TAG_ATTRIBUTE) == quantity
        }
        if (existingItem != null) return

        val item = rootTag.createChildTag(StringsXmlManager.ITEM_TAG, rootTag.namespace, itemValue, false)
        item.setAttribute(StringsXmlManager.QUANTITY_TAG_ATTRIBUTE, quantity)
        pluralTag.addSubTag(item, false)
    }

    private fun reformatFile(file: XmlFile) {
        WriteCommandAction.runWriteCommandAction(project) {
            codeStyleManager.reformat(file)
        }
    }

    private fun readLocalizedState(file: XmlFile): LocalizedFileState {
        return ApplicationManager.getApplication().runReadAction<LocalizedFileState> {
            val rootTag = file.rootTag
            val strings = mutableSetOf<String>()
            val plurals = mutableMapOf<String, Set<String>>()

            rootTag?.subTags?.forEach { tag ->
                val key = tag.getAttributeValue(StringsXmlManager.NAME_TAG_ATTRIBUTE) ?: return@forEach
                when (tag.name) {
                    StringsXmlManager.STRING_TAG -> strings += key
                    StringsXmlManager.PLURAL_TAG -> {
                        val quantities = tag.findSubTags(StringsXmlManager.ITEM_TAG)
                            .mapNotNull { it.getAttributeValue(StringsXmlManager.QUANTITY_TAG_ATTRIBUTE) }
                            .toSet()
                        plurals[key] = quantities
                    }
                }
            }

            LocalizedFileState(
                strings = strings,
                plurals = plurals,
            )
        }
    }

    private fun readPluralResource(tag: XmlTag): PluralResource? {
        val values = mutableMapOf<String, String>()
        tag.findSubTags(StringsXmlManager.ITEM_TAG).forEach { item ->
            val quantity = item.getAttributeValue(StringsXmlManager.QUANTITY_TAG_ATTRIBUTE) ?: return@forEach
            val text = item.value.trimmedText.takeIf { it.isNotBlank() } ?: return@forEach
            values[quantity] = text
        }

        if (values.isEmpty()) return null

        return PluralResource(
            zero = values[StringsXmlManager.QUANTITY_ZERO],
            one = values[StringsXmlManager.QUANTITY_ONE],
            few = values[StringsXmlManager.QUANTITY_FEW],
            many = values[StringsXmlManager.QUANTITY_MANY],
            other = values[StringsXmlManager.QUANTITY_OTHER],
            currentNumber = 1,
        )
    }

    private fun hasAllPluralQuantities(resource: Resource, existingQuantities: Set<String>): Boolean {
        return existingQuantities.containsAll(requiredPluralQuantities(resource))
    }

    private fun requiredPluralQuantities(resource: Resource): Set<String> {
        val plural = resource as? PluralResource ?: return emptySet()
        return buildSet {
            if (!plural.zero.isNullOrBlank()) add(StringsXmlManager.QUANTITY_ZERO)
            if (!plural.one.isNullOrBlank()) add(StringsXmlManager.QUANTITY_ONE)
            if (!plural.few.isNullOrBlank()) add(StringsXmlManager.QUANTITY_FEW)
            if (!plural.many.isNullOrBlank()) add(StringsXmlManager.QUANTITY_MANY)
            if (!plural.other.isNullOrBlank()) add(StringsXmlManager.QUANTITY_OTHER)
        }
    }

    private fun resolveDirectoryLanguage(file: XmlFile): ResourceDirectoryLanguage {
        val directoryName = file.virtualFile.parent?.name ?: return ResourceDirectoryLanguage.Unsupported
        return SupportedAppLanguage.resolveResourceDirectory(directoryName)
    }

    private fun getSavedBaseLanguage(): SupportedAppLanguage {
        val savedName = LocalStorage.getData(Constants.Preferences.BASE_LANGUAGE)
        return savedName
            ?.let { runCatching { SupportedAppLanguage.valueOf(it) }.getOrNull() }
            ?.takeIf { it.isTranslatable }
            ?: SupportedAppLanguage.ENGLISH
    }

    private data class BaseResourceEntry(
        val key: String,
        val resource: Resource,
    )

    private data class LocalizedXmlFile(
        val file: XmlFile,
        val directoryLanguage: ResourceDirectoryLanguage,
    )

    private data class LocalizedFileState(
        val strings: MutableSet<String>,
        val plurals: MutableMap<String, Set<String>>,
    )

    private companion object {
        private const val RESOURCES_TAG = "resources"
        private const val TRANSLATABLE_ATTRIBUTE = "translatable"
        private const val FALSE_VALUE = "false"
        private const val DEFAULT_STRINGS_XML = "<resources>\n</resources>\n"
    }
}
