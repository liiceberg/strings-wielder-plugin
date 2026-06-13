package com.liiceberg.strings.service

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.liiceberg.model.PluralResource
import com.liiceberg.module.ModuleExplorer
import com.liiceberg.module.ModuleFileFinder
import com.liiceberg.strings.KotlinResourceUsageChange
import com.liiceberg.strings.KotlinResourceUsageRewriter
import com.liiceberg.strings.StringsXmlManager
import com.liiceberg.strings.translator.SupportedAppLanguage
import kotlinx.coroutines.runBlocking

class ExistingResourcePatternApplyService(
    private val project: Project,
) {

    private val moduleExplorer = ModuleExplorer(project)
    private val kotlinResourceUsageRewriter = KotlinResourceUsageRewriter(project)

    fun apply(decisions: List<ExistingResourcePatternDecision>): ExistingResourcePatternApplyResult {
        if (decisions.isEmpty()) {
            return ExistingResourcePatternApplyResult()
        }

        val modules = ApplicationManager.getApplication().runReadAction<List<Module>> {
            moduleExplorer.getAndroidModules()
        }

        val updatedResourceEntries = runBlocking { updateResources(decisions) }
        val changedCodeFiles = rewriteCodeUsages(modules, decisions)

        return ExistingResourcePatternApplyResult(
            updatedResourceEntries = updatedResourceEntries,
            changedCodeFiles = changedCodeFiles,
        )
    }

    private suspend fun updateResources(decisions: List<ExistingResourcePatternDecision>): Int {
        var updatedResourceEntries = 0
        decisions
            .groupBy { it.moduleName }
            .forEach { (moduleName, moduleDecisions) ->
                ProgressManager.checkCanceled()
                val module = moduleExplorer.getModuleByName(moduleName) ?: return@forEach
                updatedResourceEntries += StringsXmlManager(
                    project = project,
                    module = module,
                    entries = emptyList(),
                    baseLanguage = SupportedAppLanguage.ENGLISH,
                ).applyExistingResourceChanges(
                    moduleDecisions.map { decision ->
                        when (val action = decision.action) {
                            is ExistingResourcePatternAction.Template -> {
                                StringsXmlManager.ExistingResourceChangeRequest.Template(
                                    sourceKey = decision.sourceKey,
                                    targetKey = decision.targetKey,
                                    baseFilePath = decision.filePath,
                                    baseValue = action.value,
                                    templateFormats = action.templateFormats,
                                )
                            }

                            is ExistingResourcePatternAction.Plural -> {
                                StringsXmlManager.ExistingResourceChangeRequest.Plural(
                                    sourceKey = decision.sourceKey,
                                    targetKey = decision.targetKey,
                                    baseFilePath = decision.filePath,
                                    plural = action.plural,
                                )
                            }
                        }
                    }
                )
            }
        return updatedResourceEntries
    }

    private fun rewriteCodeUsages(
        modules: List<Module>,
        decisions: List<ExistingResourcePatternDecision>,
    ): Int {
        var changedFiles = 0
        val changes = decisions.map { decision ->
            when (val action = decision.action) {
                is ExistingResourcePatternAction.Template -> KotlinResourceUsageChange.Template(
                    sourceKey = decision.sourceKey,
                    targetKey = decision.targetKey,
                    arguments = action.arguments,
                )

                is ExistingResourcePatternAction.Plural -> KotlinResourceUsageChange.Plural(
                    sourceKey = decision.sourceKey,
                    targetKey = decision.targetKey,
                    currentNumber = action.plural.currentNumber,
                )
            }
        }

        modules
            .flatMap { module -> ModuleFileFinder.getModuleKotlinFiles(module) }
            .distinctBy { it.path }
            .forEach { file ->
                ProgressManager.checkCanceled()
                if (kotlinResourceUsageRewriter.rewrite(file, changes)) {
                    changedFiles += 1
                }
            }

        return changedFiles
    }
}

data class ExistingResourcePatternDecision(
    val moduleName: String,
    val filePath: String,
    val sourceKey: String,
    val targetKey: String,
    val action: ExistingResourcePatternAction,
)

sealed interface ExistingResourcePatternAction {
    data class Template(
        val value: String,
        val templateFormats: List<String?>,
        val arguments: List<String>,
    ) : ExistingResourcePatternAction

    data class Plural(
        val plural: PluralResource,
    ) : ExistingResourcePatternAction
}

data class ExistingResourcePatternApplyResult(
    val updatedResourceEntries: Int = 0,
    val changedCodeFiles: Int = 0,
)
