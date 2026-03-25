package com.liiceberg.module

import com.intellij.openapi.project.Project
import com.liiceberg.model.ModuleContent
import com.liiceberg.strings.SearchUtil
import com.liiceberg.strings.StringResourcesProcessor
import com.liiceberg.strings.detector.PluralDetector
import com.liiceberg.strings.detector.TemplateDetector
import com.liiceberg.strings.finder.StringResourceFinder
import com.liiceberg.ui.FoundStringDialog
import com.liiceberg.ui.entity.HardcodedStringEntity
import com.liiceberg.ui.entity.SuggestionType

class ModuleAnalysisService(private val project: Project, private val isDeepAnalyze: Boolean = true) {

    private val searchUtil = SearchUtil(project)
    private val analyzer = ModuleAnalyzer(project)

    fun performAnalysis() {
        val analyzeResults = analyzer.analyzeAllModules()
        val entries = analyzeResults.flatMap { moduleContent ->
            val stringResources = StringResourceFinder
                .getModuleStrings(moduleContent.module)
                ?.assetSets
                ?.map { it.name }
                ?.toSet() ?: emptySet()
            getEntries(moduleContent, stringResources)
        }.map {
            val duplicates = findDuplicates(it.value)
            if (duplicates.isNotEmpty()) {
                it.suggestions.add(SuggestionType.DUPLICATE)
                it.duplicateOf = duplicates
            }
            val templates = TemplateDetector.detect(it.value)
            if (templates.isNotEmpty()) {
                it.suggestions.add(SuggestionType.TEMPLATE)
                it.patterns.addAll(templates)
            }
            val plurals = PluralDetector.detect(it.value)
            if (plurals.isNotEmpty()) {
                it.suggestions.add(SuggestionType.PLURAL)
                it.patterns.addAll(plurals)
            }
            it
        }
        FoundStringDialog(project, entries).show()
    }

    private fun getEntries(
        moduleContent: ModuleContent,
        stringResources: Set<String>,
    ): List<HardcodedStringEntity> {
        val entries = mutableListOf<HardcodedStringEntity>()
        val processor = StringResourcesProcessor(stringResources)
        moduleContent.strings.forEach {
            entries.addAll(processor.process(it.strings, it.file, moduleContent.module))
        }
        return entries
    }

    private fun findDuplicates(string: String): List<SearchUtil.SearchResult> {
        return if (isDeepAnalyze) {
            searchUtil.fuzzySearch(string)
        } else {
            return searchUtil.search(string)?.let { res ->
                listOf(res)
            } ?: emptyList()
        }
    }

}