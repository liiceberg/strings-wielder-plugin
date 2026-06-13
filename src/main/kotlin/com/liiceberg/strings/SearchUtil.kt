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
import com.liiceberg.utils.resolveDirectoryLanguage
import me.xdrop.fuzzywuzzy.FuzzySearch
import java.util.concurrent.ConcurrentHashMap

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
    private val indexedResourceCache = ConcurrentHashMap<String, List<IndexedResource>>()

    fun deepSearch(
        module: Module,
        string: String,
        sourceLanguage: SupportedAppLanguage?,
    ): List<SearchResult> {
        val normalizedQuery = normalizeStringForSearch(string)
        return deduplicateResults(
            fuzzySearch(module, normalizedQuery, sourceLanguage)
                    + semanticSearch(module, normalizedQuery, sourceLanguage)
        )
    }

    private fun fuzzySearch(
        module: Module,
        string: String,
        sourceLanguage: SupportedAppLanguage?,
    ): List<SearchResult> {
        return buildSearchQueries(string, sourceLanguage)
            .flatMap { query ->
                ProgressManager.checkCanceled()
                val resources = getResources(module, query.scopes)
                val normalizedQuery = normalizeStringForSearch(query.text)
                FuzzySearch
                    .extractAll(normalizedQuery, resources.keys, MIN_THRESHOLD)
                    .flatMap { resources[it.string].orEmpty() }
            }
            .let(::deduplicateResults)
    }

    private fun semanticSearch(
        module: Module,
        string: String,
        sourceLanguage: SupportedAppLanguage?,
    ): List<SearchResult> {
        return buildSearchQueries(string, sourceLanguage)
            .flatMap { query ->
                ProgressManager.checkCanceled()
                val q = normalizeStringForSearch(query.text)
                val resources = getResources(module, query.scopes)
                val matchedValues = semanticDuplicateSearcher.findSemanticDuplicates(q, resources.keys.toList())
                matchedValues.flatMap { resources[it].orEmpty() }
            }
            .let(::deduplicateResults)
    }

    private fun getIndexedResources(module: Module): List<IndexedResource> {
        return indexedResourceCache.computeIfAbsent(module.name) {
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
                addAll(
                    ApplicationManager.getApplication().runReadAction<List<IndexedResource>> {
                        val packageName = currentModule.getAndroidPackageName()
                        ModuleFileFinder.getModuleStringFiles(currentModule)
                            .distinctBy { it.path }
                            .mapNotNull { file -> psiManager.findFile(file) as? XmlFile }
                            .flatMap { file ->
                                val directoryLanguage = file.resolveDirectoryLanguage()
                                if (directoryLanguage == ResourceDirectoryLanguage.Unsupported) {
                                    return@flatMap emptyList()
                                }

                                file.rootTag?.subTags?.mapNotNull { tag ->
                                    when (tag.name) {
                                        StringsXmlManager.STRING_TAG -> {
                                            val key = tag.getAttributeValue(StringsXmlManager.NAME_TAG_ATTRIBUTE)
                                                ?: return@mapNotNull null
                                            val value = tag.value.trimmedText
                                            value.takeIf { it.isNotBlank() }?.let {
                                                IndexedResource(
                                                    result = SearchResult(
                                                        module = currentModule,
                                                        packageName = packageName,
                                                        key = key,
                                                        value = it,
                                                        resourceType = ResourceType.STRING,
                                                    ),
                                                    directoryLanguage = directoryLanguage,
                                                )
                                            }
                                        }

                                        StringsXmlManager.PLURAL_TAG -> {
                                            val key = tag.getAttributeValue(StringsXmlManager.NAME_TAG_ATTRIBUTE)
                                                ?: return@mapNotNull null
                                            resolvePluralPreview(tag.findSubTags(StringsXmlManager.ITEM_TAG))
                                                ?.takeIf { it.isNotBlank() }
                                                ?.let { value ->
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
                                                }
                                        }

                                        else -> null
                                    }
                                }.orEmpty()
                            }
                    }
                )
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
        val duplicateKey = result.duplicateKey()

        if (currentValues.any { current ->
                current.duplicateKey() == duplicateKey
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
            .replace(Regex("\\p{Punct}"), "")
            .lowercase()
    }

    private fun deduplicateResults(results: List<SearchResult>): List<SearchResult> {
        return results.distinctBy { it.duplicateKey() }
    }

    private fun SearchResult.duplicateKey(): DuplicateResourceKey {
        return DuplicateResourceKey(
            moduleName = module.name,
            packageName = packageName,
            key = key,
            resourceType = resourceType,
        )
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
        const val MIN_THRESHOLD = 90
    }
}
