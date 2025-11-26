package com.liiceberg.utils.module

import com.intellij.openapi.project.Project
import com.liiceberg.model.FileStrings
import com.liiceberg.ui.FoundStringDialog
import com.liiceberg.ui.entity.HardcodedStringEntity
import com.liiceberg.utils.strings.StringResourcesProcessor
import com.liiceberg.utils.strings.finder.StringResourceFinder

object ModuleAnalysisService {

    fun performAnalysis(project: Project) {
        val analyzer = ModuleAnalyzer(project)
        val analyzeResults = analyzer.analyzeAllModules()
        val entries = analyzeResults.flatMap { moduleContent ->
            val stringResources = StringResourceFinder
                .getModuleStrings(moduleContent.module)
                ?.assetSets
                ?.map { it.name } ?: emptyList()
            getEntries(moduleContent.strings, stringResources)
        }
        FoundStringDialog(project, entries).show()
    }

    private fun getEntries(
        files: List<FileStrings>,
        stringResources: List<String>,
    ): List<HardcodedStringEntity> {
        val entries = mutableListOf<HardcodedStringEntity>()
        val processor = StringResourcesProcessor(stringResources)
        files.forEach {
            entries.addAll(processor.process(it.strings, it.file))
        }
        return entries
    }

}