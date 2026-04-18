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

class SyncResourcesToAllAppLocalesAction : AnAction() {

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.getData(PlatformDataKeys.PROJECT) ?: return
        DumbService.getInstance(project).runWhenSmart {
            ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Syncing resources to all app locales", true) {
                override fun run(indicator: ProgressIndicator) {
                    indicator.isIndeterminate = false
                    indicator.text = "Syncing resources to all app locales"
                    runBlocking {
                        ExistingResourcesTranslationService(project).translateMissingResources(
                            mode = ExistingResourcesTranslationService.Mode.ALL_APP_LOCALES,
                        ) { description ->
                            indicator.text = "Syncing resources to all app locales"
                            indicator.text2 = description.take(120)
                        }
                    }
                }

                override fun onThrowable(error: Throwable) {
                    Messages.showErrorDialog(
                        project,
                        error.message ?: "Failed to sync resources to all app locales.",
                        "String-Wielder"
                    )
                }
            })
        }
    }
}
