package com.liiceberg.strings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.liiceberg.module.ModuleFileFinder
import com.liiceberg.strings.StringsXmlManager.Companion.QUANTITY_FEW
import com.liiceberg.strings.StringsXmlManager.Companion.QUANTITY_MANY
import com.liiceberg.strings.StringsXmlManager.Companion.QUANTITY_ONE
import com.liiceberg.strings.StringsXmlManager.Companion.QUANTITY_OTHER
import com.liiceberg.strings.StringsXmlManager.Companion.QUANTITY_ZERO
import com.liiceberg.strings.semantic.SemanticDuplicateSearcher
import com.liiceberg.strings.translator.ResourceDirectoryLanguage
import com.liiceberg.strings.translator.SupportedAppLanguage
import com.liiceberg.utils.getAndroidPackageName
import com.liiceberg.utils.getResourceDependencies
import me.xdrop.fuzzywuzzy.FuzzySearch

class SearchUtil(project: Project) {

    data class SearchResult(
        val module: Module,
        val packageName: String?,
        val key: String,
        val value: String,
        val resourceType: ResourceType,
    )

    enum class ResourceType {
        STRING,
        PLURAL,
    }

    private val psiManager = PsiManager.getInstance(project)
    private val semanticDuplicateSearcher = SemanticDuplicateSearcher()
    private val quantityValues = listOf(QUANTITY_OTHER, QUANTITY_MANY, QUANTITY_FEW, QUANTITY_ONE, QUANTITY_ZERO)
    private val resourceCache = mutableMapOf<String, List<IndexedResource>>()

    fun deepSearch(
        module: Module,
        string: String,
        sourceLanguage: SupportedAppLanguage?,
    ): List<SearchResult> {
        return (fuzzySearch(module, string, sourceLanguage) + semanticSearch(module, string, sourceLanguage)).toSet().toList()
    }

    fun search(
        module: Module,
        string: String,
        sourceLanguage: SupportedAppLanguage?,
    ): SearchResult? {
        val queries = buildSearchQueries(string, sourceLanguage)
        queries.forEach { query ->
            getResources(module, query.scopes)[normalizeStringForSearch(query.text)]?.firstOrNull()?.let { return it }
        }
        return null
    }

    private fun fuzzySearch(
        module: Module,
        string: String,
        sourceLanguage: SupportedAppLanguage?,
    ): List<SearchResult> {
        return buildSearchQueries(string, sourceLanguage)
            .flatMap { query ->
                val resources = getResources(module, query.scopes)
                val normalizedQuery = normalizeStringForSearch(query.text)
                FuzzySearch
                    .extractAll(normalizedQuery, resources.keys, MIN_THRESHOLD)
                    .flatMap { resources[it.string].orEmpty() }
            }
            .distinctBy { result ->
                DuplicateResourceKey(
                    moduleName = result.module.name,
                    packageName = result.packageName,
                    key = result.key,
                    resourceType = result.resourceType,
                )
            }
    }

    private fun semanticSearch(
        module: Module,
        string: String,
        sourceLanguage: SupportedAppLanguage?,
    ): List<SearchResult> {
        return buildSearchQueries(string, sourceLanguage)
            .flatMap { query ->
                val q = normalizeStringForSearch(query.text)
                getResources(module, query.scopes).values.flatten().filter { candidate ->
                    semanticDuplicateSearcher.isSemanticDuplicate(q, normalizeStringForSearch(candidate.value))
                }
            }
            .distinctBy { result ->
                DuplicateResourceKey(
                    moduleName = result.module.name,
                    packageName = result.packageName,
                    key = result.key,
                    resourceType = result.resourceType,
                )
            }
    }

    private fun getIndexedResources(module: Module): List<IndexedResource> {
        return resourceCache.getOrPut(module.name) {
            buildResourcesForModule(module)
        }
    }

    private fun getResources(
        module: Module,
        scopes: List<ResourceDirectoryLanguage>,
    ): Map<String, List<SearchResult>> {
        return buildMap {
            scopes.distinct().forEach { scope ->
                getIndexedResources(module)
                    .filter { it.directoryLanguage == scope }
                    .forEach { indexed ->
                        addResult(indexed.result)
                    }
            }
        }
    }

    private fun buildResourcesForModule(module: Module): List<IndexedResource> {
        val accessibleModules = ApplicationManager.getApplication().runReadAction<List<Module>> {
            buildList {
                add(module)
                addAll(module.getResourceDependencies())
            }.distinctBy { it.name }
        }

        return buildList {
            accessibleModules.forEach { currentModule ->
                ProgressManager.checkCanceled()
                val packageName = ApplicationManager.getApplication().runReadAction<String?> {
                    currentModule.getAndroidPackageName()
                }
                ModuleFileFinder.getModuleStringFiles(currentModule)
                    .distinctBy { it.path }
                    .mapNotNull { file ->
                        ApplicationManager.getApplication().runReadAction<XmlFile?> {
                            psiManager.findFile(file) as? XmlFile
                        }
                    }
                    .forEach { file ->
                        ApplicationManager.getApplication().runReadAction {
                            val directoryLanguage = resolveDirectoryLanguage(file)
                            if (directoryLanguage == ResourceDirectoryLanguage.Unsupported) {
                                return@runReadAction
                            }

                            file.rootTag?.subTags?.forEach { tag ->
                                when (tag.name) {
                                    StringsXmlManager.STRING_TAG -> {
                                        val key = tag.getAttributeValue(StringsXmlManager.NAME_TAG_ATTRIBUTE) ?: return@forEach
                                        val value = tag.value.trimmedText
                                        if (value.isNotBlank()) {
                                            add(
                                                IndexedResource(
                                                    result = SearchResult(
                                                        module = currentModule,
                                                        packageName = packageName,
                                                        key = key,
                                                        value = value,
                                                        resourceType = ResourceType.STRING,
                                                    ),
                                                    directoryLanguage = directoryLanguage,
                                                )
                                            )
                                        }
                                    }

                                    StringsXmlManager.PLURAL_TAG -> {
                                        val key = tag.getAttributeValue(StringsXmlManager.NAME_TAG_ATTRIBUTE) ?: return@forEach
                                        resolvePluralPreview(tag.findSubTags(StringsXmlManager.ITEM_TAG))?.takeIf { it.isNotBlank() }?.let { value ->
                                            add(
                                                IndexedResource(
                                                    result = SearchResult(
                                                        module = currentModule,
                                                        packageName = packageName,
                                                        key = key,
                                                        value = value,
                                                        resourceType = ResourceType.PLURAL,
                                                    ),
                                                    directoryLanguage = directoryLanguage,
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
            }
        }
    }

    private fun buildSearchQueries(
        text: String,
        sourceLanguage: SupportedAppLanguage?,
    ): List<SearchQuery> {
        if (sourceLanguage == SupportedAppLanguage.NON_TRANSLATABLE) {
            return listOf(SearchQuery(text, listOf(ResourceDirectoryLanguage.Default)))
        }

        val queries = mutableListOf<SearchQuery>()
        sourceLanguage?.let { language ->
            queries += SearchQuery(
                text = text,
                scopes = listOf(ResourceDirectoryLanguage.Known(language)),
            )
        }
        queries += SearchQuery(
            text = text,
            scopes = listOf(ResourceDirectoryLanguage.Default),
        )

        return queries.distinctBy { normalizeStringForSearch(it.text) to it.scopes }
    }

    private fun MutableMap<String, List<SearchResult>>.addResult(result: SearchResult) {
        val normalizedValue = normalizeStringForSearch(result.value)
        val currentValues = this[normalizedValue].orEmpty()
        val duplicateKey = DuplicateResourceKey(
            moduleName = result.module.name,
            packageName = result.packageName,
            key = result.key,
            resourceType = result.resourceType,
        )

        if (currentValues.any { current ->
                DuplicateResourceKey(
                    moduleName = current.module.name,
                    packageName = current.packageName,
                    key = current.key,
                    resourceType = current.resourceType,
                ) == duplicateKey
            }) {
            return
        }

        this[normalizedValue] = currentValues + result
    }

    private fun resolvePluralPreview(items: Array<XmlTag>): String? {
        val map = mutableMapOf<String, String>()
        items.forEach { item ->
            item.getAttributeValue(StringsXmlManager.QUANTITY_TAG_ATTRIBUTE)?.let { quantity ->
                map[quantity] = item.value.trimmedText
            }
        }
        quantityValues.forEach { quantity ->
            val value = map[quantity]
            if (!value.isNullOrBlank()) return value
        }
        return null
    }

    private fun normalizeStringForSearch(input: String): String {
        return input
            .trim()
            .replace(Regex("\\s+"), " ")
            .replace('_', ' ')
            .replace('-', ' ')
            .replace(Regex("[“”\"'.,!?]"), "")
            .lowercase()
    }

    private fun resolveDirectoryLanguage(file: XmlFile): ResourceDirectoryLanguage {
        val directoryName = file.virtualFile.parent?.name ?: return ResourceDirectoryLanguage.Unsupported
        return SupportedAppLanguage.resolveResourceDirectory(directoryName)
    }

    private data class IndexedResource(
        val result: SearchResult,
        val directoryLanguage: ResourceDirectoryLanguage,
    )

    private data class SearchQuery(
        val text: String,
        val scopes: List<ResourceDirectoryLanguage>,
    )

    private data class DuplicateResourceKey(
        val moduleName: String,
        val packageName: String?,
        val key: String,
        val resourceType: ResourceType,
    )

    private companion object {
        const val MIN_THRESHOLD = 95
    }
}
