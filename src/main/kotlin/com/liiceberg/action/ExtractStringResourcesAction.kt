package com.liiceberg.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.project.DumbService
import com.liiceberg.strings.service.ExtractStringsAnalysisService

class ExtractStringResourcesAction : AnAction() {

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.getData(PlatformDataKeys.PROJECT) ?: return
        DumbService.getInstance(project).runWhenSmart {
            ExtractStringsAnalysisService(project).performAnalysis()
        }
    }
}
