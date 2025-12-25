package com.liiceberg.module

import com.intellij.openapi.project.Project
import com.liiceberg.model.ModuleContent
import com.liiceberg.strings.SearchUtil
import com.liiceberg.strings.StringResourcesProcessor
import com.liiceberg.strings.finder.StringResourceFinder
import com.liiceberg.ui.FoundStringDialog
import com.liiceberg.ui.entity.HardcodedStringEntity

class ModuleAnalysisService(private val project: Project) {

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
            it.strings.forEach { str -> printDuplicates(str) }
        }
        return entries
    }

    private fun printDuplicates(string: String) {
        println("string: $string")
        println(searchUtil.search(string)?.tag?.value?.text)
        println(searchUtil.fuzzySearch(string).map { it.tag.value.text })
    }

}