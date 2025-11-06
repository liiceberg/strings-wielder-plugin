package com.liiceberg.utils.module

import com.intellij.openapi.project.Project

object ModuleAnalysisService {

    fun performAnalysis(project: Project) {
        val analyzer = ModuleAnalyzer(project)
        val results = analyzer.analyzeAllModules()

        results.forEach {
            println(it.module)
            println(it.strings)
        }
    }

}