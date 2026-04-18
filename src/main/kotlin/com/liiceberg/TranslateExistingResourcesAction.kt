package com.liiceberg

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.ui.Messages
import com.liiceberg.strings.ExistingResourcesTranslationService
import kotlinx.coroutines.runBlocking

class TranslateExistingResourcesAction : AnAction() {

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.getData(PlatformDataKeys.PROJECT) ?: return
        DumbService.getInstance(project).runWhenSmart {
            ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Translating existing resources", true) {
                override fun run(indicator: ProgressIndicator) {
                    indicator.isIndeterminate = false
                    indicator.text = "Translating existing resources"
                    runBlocking {
                        ExistingResourcesTranslationService(project).translateMissingResources { description ->
                            indicator.text = "Translating existing resources"
                            indicator.text2 = description.take(120)
                        }
                    }
                }

                override fun onThrowable(error: Throwable) {
                    Messages.showErrorDialog(
                        project,
                        error.message ?: "Failed to translate existing resources.",
                        "String-Wielder"
                    )
                }
            })
        }
    }
}
