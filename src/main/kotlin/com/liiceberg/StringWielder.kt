package com.liiceberg

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.project.DumbService
import com.liiceberg.module.ModuleAnalysisService

class StringWielder : AnAction() {

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.getData(PlatformDataKeys.PROJECT) ?: return
        DumbService.getInstance(project).runWhenSmart {
            ModuleAnalysisService(project).performAnalysis()
        }
    }
}
