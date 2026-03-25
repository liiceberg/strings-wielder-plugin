package com.liiceberg.strings

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.liiceberg.module.ModuleExplorer
import com.liiceberg.module.ModuleFileFinder
import me.xdrop.fuzzywuzzy.FuzzySearch

class SearchUtil(project: Project) {

    data class SearchResult(
        val tag: XmlTag,
        val file: XmlFile,
    )

    private val psiManager = PsiManager.getInstance(project)
    private val moduleExplorer = ModuleExplorer(project)
    private val quantityValues = listOf("other", "many", "few", "one", "zero")
    private val resources = getAllStrings()

    fun search(string: String): SearchResult? {
        return resources[normalizeStringForSearch(string)]
    }

    fun fuzzySearch(string: String): List<SearchResult> {
        val query = normalizeStringForSearch(string)
        return FuzzySearch
            .extractAll(query, resources.keys, MIN_THRESHOLD)
            .mapNotNull { resources[it.string] }

    }

    private fun getAllStrings(): Map<String, SearchResult> {
        return buildMap {
            moduleExplorer.getAndroidModules().map { module ->
                ModuleFileFinder.getModuleStringFiles(module)
            }.flatten().mapNotNull { file ->
                psiManager.findFile(file) as? XmlFile
            }.map { file ->
                file.rootTag?.subTags
                    ?.forEach { tag ->
                        val str: String? = when (tag.name) {
                            StringsXmlManager.STRING_TAG -> {
                                tag.value.trimmedText
                            }

                            StringsXmlManager.PLURAL_TAG -> {
                                val map = mutableMapOf<String, String>()
                                for (item in tag.findSubTags(StringsXmlManager.ITEM_TAG)) {
                                    item.getAttributeValue(StringsXmlManager.QUANTITY_TAG_ATTRIBUTE)?.let {
                                        map.put(it, item.value.trimmedText)
                                    }
                                }
                                var ans: String? = null
                                for (v in quantityValues) {
                                    if (!map[v].isNullOrEmpty()) {
                                        ans = map[v]
                                        break
                                    }
                                }
                                ans
                            }

                            else -> null
                        }
                        str?.let {
                            put(
                                key = normalizeStringForSearch(it),
                                value = SearchResult(tag, file),
                            )
                        }
                    }
            }
        }
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

    private companion object {
        //        TODO: test this value
        const val MIN_THRESHOLD = 95
    }
}