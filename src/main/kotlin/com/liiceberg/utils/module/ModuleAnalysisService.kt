package com.liiceberg.utils.module

import com.intellij.openapi.project.Project
import com.liiceberg.model.ModuleContent
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
        }
        return entries
    }

}