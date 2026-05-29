package com.liiceberg.strings.analysis

import com.intellij.openapi.module.Module
import com.liiceberg.strings.SearchUtil
import com.liiceberg.strings.detector.PluralDetector
import com.liiceberg.strings.detector.TemplateDetector
import com.liiceberg.strings.translator.SupportedAppLanguage
import com.liiceberg.ui.entity.HardcodedStringEntity
import com.liiceberg.ui.entity.SuggestionType

class ResourceSuggestionService(
    private val searchUtil: SearchUtil,
    private val isDeepAnalyze: Boolean,
) {

    fun enrichEntry(entry: HardcodedStringEntity): HardcodedStringEntity {
        val duplicates = findDuplicates(searchUtil, entry.module, entry.value, entry.sourceLanguage)
        if (duplicates.isNotEmpty()) {
            entry.suggestions.add(SuggestionType.DUPLICATE)
            entry.duplicateOf = duplicates
        }

        val templates = TemplateDetector.detect(entry.value)
        if (templates.isNotEmpty()) {
            entry.suggestions.add(SuggestionType.TEMPLATE)
            entry.patterns.addAll(templates.filterNot { existing -> entry.patterns.contains(existing) })
        }

        val plurals = PluralDetector.detect(entry.value)
        if (plurals.isNotEmpty()) {
            entry.suggestions.add(SuggestionType.PLURAL)
            entry.patterns.addAll(plurals.filterNot { existing -> entry.patterns.contains(existing) })
        }

        return entry
    }

    private fun findDuplicates(
        searchUtil: SearchUtil,
        module: Module,
        string: String,
        sourceLanguage: SupportedAppLanguage?,
    ): List<SearchUtil.SearchResult> {
        return if (isDeepAnalyze) {
            searchUtil.deepSearch(module, string, sourceLanguage)
        } else {
            searchUtil.search(module, string, sourceLanguage)?.let(::listOf).orEmpty()
        }
    }
}
