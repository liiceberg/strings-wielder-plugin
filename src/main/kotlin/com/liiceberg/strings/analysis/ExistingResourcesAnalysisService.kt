package com.liiceberg.strings.analysis

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.liiceberg.module.ModuleExplorer
import com.liiceberg.module.ModuleFileFinder
import com.liiceberg.strings.SearchUtil
import com.liiceberg.strings.StringsXmlManager.Companion.ITEM_TAG
import com.liiceberg.strings.StringsXmlManager.Companion.NAME_TAG_ATTRIBUTE
import com.liiceberg.strings.StringsXmlManager.Companion.PLURAL_TAG
import com.liiceberg.strings.StringsXmlManager.Companion.QUANTITY_FEW
import com.liiceberg.strings.StringsXmlManager.Companion.QUANTITY_MANY
import com.liiceberg.strings.StringsXmlManager.Companion.QUANTITY_ONE
import com.liiceberg.strings.StringsXmlManager.Companion.QUANTITY_OTHER
import com.liiceberg.strings.StringsXmlManager.Companion.QUANTITY_TAG_ATTRIBUTE
import com.liiceberg.strings.StringsXmlManager.Companion.QUANTITY_ZERO
import com.liiceberg.strings.StringsXmlManager.Companion.STRING_TAG
import com.liiceberg.strings.translator.ResourceDirectoryLanguage
import com.liiceberg.strings.translator.SupportedAppLanguage
import com.liiceberg.ui.entity.HardcodedStringEntity
import com.liiceberg.ui.entity.SuggestionType
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class ExistingResourcesAnalysisService(
    private val project: Project,
    private val isDeepAnalyze: Boolean = true,
) {

    private val moduleExplorer = ModuleExplorer(project)
    private val psiManager = PsiManager.getInstance(project)
    private val quantityValues = listOf(QUANTITY_OTHER, QUANTITY_MANY, QUANTITY_FEW, QUANTITY_ONE, QUANTITY_ZERO)

    suspend fun analyze(
        onProgress: (String) -> Unit = {},
    ): ExistingResourcesAnalysisReport = coroutineScope {
        val searchUtil = SearchUtil(project)
        val modules = ApplicationManager.getApplication().runReadAction<List<Module>> {
            moduleExplorer.getAndroidModules()
        }
        val findings = mutableListOf<ExistingResourceFinding>()
        val seenProjectEntries = mutableSetOf<ScanEntryKey>()
        var analyzedCount = 0

        val semaphore = Semaphore(MAX_ENTRY_ENRICHMENT_CONCURRENCY)

        modules.forEach { module ->
            ProgressManager.checkCanceled()
            onProgress("Analyzing module ${module.name}")

            val moduleEntries = collectModuleEntries(module)
                .filter { entry ->
                    seenProjectEntries.add(
                        ScanEntryKey(
                            filePath = entry.filePath,
                            key = entry.key,
                            value = entry.entity.value,
                            resourceType = entry.resourceType,
                        )
                    )
                }
            analyzedCount += moduleEntries.size

            moduleEntries.map { entry ->
                async {
                    semaphore.withPermit {
                        ProgressManager.checkCanceled()
                        val duplicates = findDuplicates(searchUtil, entry.entity.module, entry.entity.value)
                        buildFindingOrNull(entry, duplicates)
                    }
                }
            }.awaitAll()
                .filterNotNull()
                .forEach(findings::add)
        }

        ExistingResourcesAnalysisReport(
            analyzedCount = analyzedCount,
            findings = findings.sortedWith(
                compareBy<ExistingResourceFinding> { it.moduleName }
                    .thenBy { it.key }
                    .thenBy { it.resourceType.name }
            ),
        )
    }

    private fun collectModuleEntries(module: Module): List<ExistingResourceEntry> {
        return ApplicationManager.getApplication().runReadAction<List<ExistingResourceEntry>> {
            ModuleFileFinder.getModuleStringFiles(module)
                .distinctBy { it.path }
                .mapNotNull { psiManager.findFile(it) as? XmlFile }
                .filter { isBaseLocaleFile(it) }
                .flatMap { file ->
                    file.rootTag?.subTags?.mapNotNull { tag ->
                        val key = tag.getAttributeValue(NAME_TAG_ATTRIBUTE) ?: return@mapNotNull null
                        when (tag.name) {
                            STRING_TAG -> {
                                val value = tag.value.trimmedText.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                                ExistingResourceEntry(
                                    entity = HardcodedStringEntity(
                                        key = key,
                                        value = value,
                                        isSelected = false,
                                        virtualFile = file.virtualFile,
                                        module = module,
                                        sourceLanguage = null,
                                    ),
                                    key = key,
                                    filePath = file.virtualFile.path,
                                    resourceType = SearchUtil.ResourceType.STRING,
                                )
                            }

                            PLURAL_TAG -> {
                                val value = resolvePluralPreview(tag.findSubTags(ITEM_TAG)) ?: return@mapNotNull null
                                ExistingResourceEntry(
                                    entity = HardcodedStringEntity(
                                        key = key,
                                        value = value,
                                        isSelected = false,
                                        virtualFile = file.virtualFile,
                                        module = module,
                                        sourceLanguage = null,
                                    ),
                                    key = key,
                                    filePath = file.virtualFile.path,
                                    resourceType = SearchUtil.ResourceType.PLURAL,
                                )
                            }

                            else -> null
                        }
                    }.orEmpty()
                }
        }
    }

    private fun buildFindingOrNull(
        entry: ExistingResourceEntry,
        duplicates: List<SearchUtil.SearchResult>,
    ): ExistingResourceFinding? {
        val filteredDuplicates = duplicates.filterNot { candidate ->
            candidate.module.name == entry.entity.module.name &&
                candidate.key == entry.key &&
                candidate.resourceType == entry.resourceType
        }
        val suggestions = mutableSetOf<SuggestionType>()
        if (filteredDuplicates.isNotEmpty()) {
            suggestions.add(SuggestionType.DUPLICATE)
        }

        if (entry.resourceType == SearchUtil.ResourceType.STRING) {
            val hasRawNumericValue = containsRawNumericValue(entry.entity.value)
            if (hasRawNumericValue) {
                suggestions.add(SuggestionType.TEMPLATE)
                suggestions.add(SuggestionType.PLURAL)
            }
        }

        if (suggestions.isEmpty()) {
            return null
        }

        return ExistingResourceFinding(
            moduleName = entry.entity.module.name,
            filePath = entry.filePath,
            key = entry.key,
            value = entry.entity.value,
            sourceLanguage = entry.entity.sourceLanguage,
            resourceType = entry.resourceType,
            suggestions = suggestions.toSet(),
            duplicates = filteredDuplicates,
        )
    }

    private fun isBaseLocaleFile(file: XmlFile): Boolean {
        val directoryName = file.virtualFile.parent?.name ?: return false
        return SupportedAppLanguage.resolveResourceDirectory(directoryName) == ResourceDirectoryLanguage.Default
    }

    private fun resolvePluralPreview(items: Array<XmlTag>): String? {
        val map = mutableMapOf<String, String>()
        items.forEach { item ->
            item.getAttributeValue(QUANTITY_TAG_ATTRIBUTE)?.let { quantity ->
                map[quantity] = item.value.trimmedText
            }
        }
        quantityValues.forEach { quantity ->
            val value = map[quantity]
            if (!value.isNullOrBlank()) return value
        }
        return null
    }

    private fun containsRawNumericValue(text: String): Boolean {
        val withoutPlaceholders = text.replace(FORMAT_PLACEHOLDER_REGEX, " ")
        return RAW_NUMBER_REGEX.containsMatchIn(withoutPlaceholders)
    }

    private fun findDuplicates(
        searchUtil: SearchUtil,
        module: Module,
        string: String,
    ): List<SearchUtil.SearchResult> {
        return if (isDeepAnalyze) {
            searchUtil.deepSearch(module, string, null)
        } else {
            searchUtil.search(module, string, null)?.let(::listOf).orEmpty()
        }
    }

    private data class ExistingResourceEntry(
        val entity: HardcodedStringEntity,
        val key: String,
        val filePath: String,
        val resourceType: SearchUtil.ResourceType,
    )

    private data class ScanEntryKey(
        val filePath: String,
        val key: String,
        val value: String,
        val resourceType: SearchUtil.ResourceType,
    )

    companion object {
        private const val MAX_ENTRY_ENRICHMENT_CONCURRENCY = 4
        private val RAW_NUMBER_REGEX = Regex("""\b\d+\b""")
        private val FORMAT_PLACEHOLDER_REGEX = Regex("""%([0-9]\$)?[sdf]""")
    }
}

data class ExistingResourcesAnalysisReport(
    val analyzedCount: Int,
    val findings: List<ExistingResourceFinding>,
) {
    val duplicateCount: Int = findings.count { SuggestionType.DUPLICATE in it.suggestions }
    val templateCount: Int = findings.count { SuggestionType.TEMPLATE in it.suggestions }
    val pluralCount: Int = findings.count { SuggestionType.PLURAL in it.suggestions }
}

data class ExistingResourceFinding(
    val moduleName: String,
    val filePath: String,
    val key: String,
    val value: String,
    val sourceLanguage: SupportedAppLanguage?,
    val resourceType: SearchUtil.ResourceType,
    val suggestions: Set<SuggestionType>,
    val duplicates: List<SearchUtil.SearchResult>,
)
