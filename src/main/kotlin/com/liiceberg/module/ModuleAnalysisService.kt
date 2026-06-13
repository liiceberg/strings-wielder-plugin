package com.liiceberg.module

import com.android.tools.idea.concurrency.coroutineScope
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.liiceberg.model.ModuleContent
import com.liiceberg.strings.SearchUtil
import com.liiceberg.strings.StringResourcesProcessor
import com.liiceberg.strings.analysis.ResourceSuggestionService
import com.liiceberg.strings.finder.StringResourceFinder
import com.liiceberg.ui.FoundStringDialog
import com.liiceberg.ui.entity.HardcodedStringEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class ModuleAnalysisService(private val project: Project, private val isDeepAnalyze: Boolean = true) {

    private val analyzer = ModuleAnalyzer(project)

    fun performAnalysis() {
        project.coroutineScope.launch {
            withBackgroundProgress(project, "Preparing strings") {
                val searchUtil = SearchUtil(project)
                val suggestionService = ResourceSuggestionService(searchUtil, isDeepAnalyze)
                val analyzeResults = analyzer.analyzeAllModules()
                val entries = buildEntries(analyzeResults)
                val enrichedEntries = enrichEntries(entries, suggestionService)

                withContext(Dispatchers.EDT) {
                    FoundStringDialog(project, enrichedEntries, isDeepAnalyze, searchUtil).show()
                }
            }
        }

    }

    private suspend fun buildEntries(
        analyzeResults: List<ModuleContent>,
    ): List<HardcodedStringEntity> = coroutineScope {
        val entrySemaphore = Semaphore(MAX_ENTRY_BUILD_CONCURRENCY)

        analyzeResults
            .map { moduleContent ->
                async {
                    entrySemaphore.withPermit {
                        ProgressManager.checkCanceled()
                        val stringResources = ApplicationManager.getApplication().runReadAction<Set<String>> {
                            StringResourceFinder.getAccessibleResourceNames(moduleContent.module)
                        }
                        getEntries(moduleContent, stringResources)
                    }
                }
            }
            .awaitAll()
            .flatten()
    }

    private suspend fun enrichEntries(
        entries: List<HardcodedStringEntity>,
        suggestionService: ResourceSuggestionService,
    ): List<HardcodedStringEntity> = coroutineScope {
        val enrichmentSemaphore = Semaphore(MAX_ENTRY_ENRICHMENT_CONCURRENCY)

        entries.map { entry ->
            async {
                enrichmentSemaphore.withPermit {
                    ProgressManager.checkCanceled()
                    suggestionService.enrichEntry(entry)
                }
            }
        }.awaitAll()
    }

    private suspend fun getEntries(
        moduleContent: ModuleContent,
        stringResources: Set<String>,
    ): List<HardcodedStringEntity> = coroutineScope {

        val processor = StringResourcesProcessor(stringResources)
        val processingSemaphore = Semaphore(MAX_KEY_GENERATION_CONCURRENCY)

        moduleContent.strings
            .map { fileStrings ->
                async {
                    processingSemaphore.withPermit {
                        ProgressManager.checkCanceled()
                        processor.process(
                            fileStrings.strings,
                            fileStrings.file,
                            moduleContent.module
                        )
                    }
                }
            }
            .awaitAll()
            .flatten()
    }

    private companion object {
        private const val MAX_ENTRY_BUILD_CONCURRENCY = 4
        private const val MAX_ENTRY_ENRICHMENT_CONCURRENCY = 4
        private const val MAX_KEY_GENERATION_CONCURRENCY = 6
    }

}
