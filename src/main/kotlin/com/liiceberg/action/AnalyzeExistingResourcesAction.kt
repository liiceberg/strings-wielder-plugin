package com.liiceberg.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.ui.Messages
import com.liiceberg.strings.service.ExistingResourcesAnalysisReport
import com.liiceberg.strings.service.ExistingResourcesAnalysisService
import com.liiceberg.ui.ExistingResourcesAnalysisDialog
import kotlinx.coroutines.runBlocking

class AnalyzeExistingResourcesAction : AnAction() {

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.getData(PlatformDataKeys.PROJECT) ?: return
        DumbService.getInstance(project).runWhenSmart {
            ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Analyzing existing resources", true) {
                private var report: ExistingResourcesAnalysisReport? = null

                override fun run(indicator: ProgressIndicator) {
                    indicator.isIndeterminate = false
                    indicator.text = "Analyzing existing resources"
                    runBlocking {
                        report = ExistingResourcesAnalysisService(project).analyze { description ->
                            indicator.text = "Analyzing existing resources"
                            indicator.text2 = description.take(120)
                        }
                    }
                }

                override fun onSuccess() {
                    val result = report ?: return
                    ExistingResourcesAnalysisDialog(project, result).show()
                }

                override fun onThrowable(error: Throwable) {
                    Messages.showErrorDialog(
                        project,
                        error.message ?: "Failed to analyze existing resources.",
                        "String-Wielder"
                    )
                }
            })
        }
    }
}
