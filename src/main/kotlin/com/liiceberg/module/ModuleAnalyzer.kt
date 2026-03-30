package com.liiceberg.module

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.liiceberg.model.FileContent
import com.liiceberg.model.ModuleContent
import com.liiceberg.strings.finder.KotlinHardCodedStringFinder

class ModuleAnalyzer(project: Project) {

    private val moduleExplorer = ModuleExplorer(project)
    private val stringFinder = KotlinHardCodedStringFinder(project)

    suspend fun analyzeAllModules(): List<ModuleContent> {
        return moduleExplorer.getAndroidModules().map {
            analyzeModule(it)
        }
    }

    suspend fun analyzeModuleByName(moduleName: String): ModuleContent? {
        return moduleExplorer.getModuleByName(moduleName)?.let { analyzeModule(it) }
    }

    private suspend fun analyzeModule(module: Module): ModuleContent {
        val kotlinFiles = ModuleFileFinder.getModuleKotlinFiles(module)
        val strings = kotlinFiles.map {
            val strings = extractStringsFromFile(it)
            FileContent(it, strings)
        }
        return ModuleContent(
            module = module,
            strings = strings,
        )
    }

    private suspend fun extractStringsFromFile(file: VirtualFile): List<String> {
        return stringFinder.findHardCodedStrings(file)
    }
}