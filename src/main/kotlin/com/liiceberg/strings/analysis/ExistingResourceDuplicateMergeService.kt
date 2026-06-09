package com.liiceberg.strings.analysis

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.liiceberg.module.ModuleExplorer
import com.liiceberg.module.ModuleFileFinder
import com.liiceberg.strings.KotlinResourceReferenceReplacement
import com.liiceberg.strings.KotlinResourceReferenceReplacer
import com.liiceberg.strings.SearchUtil
import com.liiceberg.strings.StringsXmlManager

class ExistingResourceDuplicateMergeService(
    private val project: Project,
) {

    private val moduleExplorer = ModuleExplorer(project)
    private val kotlinResourceReferenceReplacer = KotlinResourceReferenceReplacer(project)

    fun apply(decisions: List<DuplicateMergeDecision>): DuplicateMergeResult {
        if (decisions.isEmpty()) {
            return DuplicateMergeResult()
        }

        val modules = ApplicationManager.getApplication().runReadAction<List<Module>> {
            moduleExplorer.getAndroidModules()
        }
        val compatibleDecisions = decisions.filter { decision ->
            decision.resourcesToRemove.all { it.resourceType == decision.keepResource.resourceType }
        }
        val replacedFiles = replaceCodeReferences(modules, compatibleDecisions)
        val deletedResources = deleteMergedResources(compatibleDecisions)

        return DuplicateMergeResult(
            replacedFiles = replacedFiles,
            deletedResources = compatibleDecisions
                .flatMap { it.resourcesToRemove }
                .distinctBy { listOf(it.moduleName, it.key, it.resourceType.name).joinToString("|") }
                .size,
            deletedResourceEntries = deletedResources,
            skippedDecisions = decisions.size - compatibleDecisions.size,
        )
    }

    private fun replaceCodeReferences(
        modules: List<Module>,
        decisions: List<DuplicateMergeDecision>,
    ): Int {
        var changedFiles = 0
        val replacements = decisions.flatMap { decision ->
            decision.resourcesToRemove.map { resource ->
                KotlinResourceReferenceReplacement(
                    fromKey = resource.key,
                    toKey = decision.keepResource.key,
                    resourceType = resource.resourceType,
                )
            }
        }

        modules
            .flatMap { module -> ModuleFileFinder.getModuleKotlinFiles(module) }
            .distinctBy { it.path }
            .forEach { file ->
                ProgressManager.checkCanceled()
                if (kotlinResourceReferenceReplacer.replace(file, replacements)) {
                    changedFiles += 1
                }
            }

        return changedFiles
    }

    private fun deleteMergedResources(decisions: List<DuplicateMergeDecision>): Int {
        var deletedCount = 0
        decisions
            .flatMap { it.resourcesToRemove }
            .distinctBy { listOf(it.moduleName, it.key, it.resourceType.name).joinToString("|") }
            .groupBy { it.moduleName }
            .forEach { (moduleName, resources) ->
                ProgressManager.checkCanceled()
                val module = moduleExplorer.getModuleByName(moduleName) ?: return@forEach
                deletedCount += StringsXmlManager(
                    project = project,
                    module = module,
                    entries = emptyList(),
                    baseLanguage = com.liiceberg.strings.translator.SupportedAppLanguage.ENGLISH,
                ).deleteResources(
                    resources.map { resource ->
                        StringsXmlManager.ResourceDeletionRequest(
                            key = resource.key,
                            resourceType = resource.resourceType,
                        )
                    }
                )
            }

        return deletedCount
    }
}

data class DuplicateMergeDecision(
    val keepResource: DuplicateResourceRef,
    val resourcesToRemove: List<DuplicateResourceRef>,
)

data class DuplicateResourceRef(
    val moduleName: String,
    val key: String,
    val resourceType: SearchUtil.ResourceType,
)

data class DuplicateMergeResult(
    val replacedFiles: Int = 0,
    val deletedResources: Int = 0,
    val deletedResourceEntries: Int = 0,
    val skippedDecisions: Int = 0,
)
