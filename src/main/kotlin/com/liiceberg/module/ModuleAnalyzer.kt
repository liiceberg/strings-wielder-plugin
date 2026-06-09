package com.liiceberg.module

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.liiceberg.model.FileContent
import com.liiceberg.model.ModuleContent
import com.liiceberg.strings.finder.KotlinHardCodedStringFinder
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class ModuleAnalyzer(project: Project) {

    private val moduleExplorer = ModuleExplorer(project)
    private val stringFinder = KotlinHardCodedStringFinder(project)

    suspend fun analyzeAllModules(): List<ModuleContent> = coroutineScope {
        val moduleSemaphore = Semaphore(MAX_MODULE_ANALYSIS_CONCURRENCY)
        val fileSemaphore = Semaphore(MAX_FILE_ANALYSIS_CONCURRENCY)
        val modules = readAction { moduleExplorer.getAndroidModules() }

        modules.map { module ->
            async {
                moduleSemaphore.withPermit {
                    ProgressManager.checkCanceled()
                    analyzeModule(module, fileSemaphore)
                }
            }
        }.awaitAll()
    }

    suspend fun analyzeModuleByName(moduleName: String): ModuleContent? {
        val module = readAction { moduleExplorer.getModuleByName(moduleName) } ?: return null
        val fileSemaphore = Semaphore(MAX_FILE_ANALYSIS_CONCURRENCY)
        return analyzeModule(module, fileSemaphore)
    }

    private suspend fun analyzeModule(
        module: Module,
        fileSemaphore: Semaphore,
    ): ModuleContent = coroutineScope {
        val kotlinFiles = ApplicationManager.getApplication().runReadAction<List<VirtualFile>> {
            ModuleFileFinder.getModuleKotlinFiles(module)
        }
        val strings = kotlinFiles.map { file ->
            async {
                fileSemaphore.withPermit {
                    ProgressManager.checkCanceled()
                    FileContent(file, extractStringsFromFile(file))
                }
            }
        }.awaitAll()

        ModuleContent(
            module = module,
            strings = strings,
        )
    }

    private suspend fun extractStringsFromFile(file: VirtualFile): List<String> {
        return stringFinder.findHardCodedStrings(file)
    }

    private companion object {
        private const val MAX_MODULE_ANALYSIS_CONCURRENCY = 4
        private const val MAX_FILE_ANALYSIS_CONCURRENCY = 8
    }
}
