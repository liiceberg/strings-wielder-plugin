package com.liiceberg.strings.service

import com.android.tools.idea.concurrency.coroutineScope
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.liiceberg.model.StringEntity
import com.liiceberg.model.ModuleContent
import com.liiceberg.model.SuggestionType
import com.liiceberg.module.ModuleAnalyzer
import com.liiceberg.strings.SearchUtil
import com.liiceberg.strings.StringResourcesProcessor
import com.liiceberg.strings.detector.PluralDetector
import com.liiceberg.strings.detector.TemplateDetector
import com.liiceberg.strings.finder.StringResourceFinder
import com.liiceberg.ui.FoundStringDialog
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class ExtractStringsAnalysisService(private val project: Project) {

    private val analyzer = ModuleAnalyzer(project)
    private val searchUtil = SearchUtil(project)

    fun performAnalysis() {
        project.coroutineScope.launch {
            withBackgroundProgress(project, "Preparing strings") {
                val analyzeResults = analyzer.analyzeAllModules()
                val entries = buildEntries(analyzeResults)
                val enrichedEntries = enrichEntries(entries)

                withContext(Dispatchers.EDT) {
                    FoundStringDialog(project, enrichedEntries, searchUtil).show()
                }
            }
        }

    }

    private suspend fun buildEntries(
        analyzeResults: List<ModuleContent>,
    ): List<StringEntity> = coroutineScope {

        analyzeResults
            .map { moduleContent ->
                async {
                    ProgressManager.checkCanceled()
                    val stringResources = ApplicationManager.getApplication().runReadAction<Set<String>> {
                        StringResourceFinder.getAccessibleResourceNames(moduleContent.module)
                    }
                    getEntries(moduleContent, stringResources)
                }
            }
            .awaitAll()
            .flatten()
    }

    private suspend fun getEntries(
        moduleContent: ModuleContent,
        stringResources: Set<String>,
    ): List<StringEntity> = coroutineScope {

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

    private suspend fun enrichEntries(
        entries: List<StringEntity>,
    ): List<StringEntity> = coroutineScope {
        val enrichmentSemaphore = Semaphore(MAX_ENTRY_ENRICHMENT_CONCURRENCY)

        entries.map { entry ->
            async {
                enrichmentSemaphore.withPermit {
                    ProgressManager.checkCanceled()
                    val duplicates = searchUtil.deepSearch(entry.module, entry.value, entry.sourceLanguage)
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
                    entry
                }
            }
        }.awaitAll()
    }

    private companion object {
        private const val MAX_ENTRY_ENRICHMENT_CONCURRENCY = 4
        private const val MAX_KEY_GENERATION_CONCURRENCY = 6
    }

}
