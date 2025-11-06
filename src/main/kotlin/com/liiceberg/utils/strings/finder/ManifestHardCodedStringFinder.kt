package com.liiceberg.utils.strings.finder

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

class ManifestHardCodedStringFinder(project: Project) : HardCodedStringFinder(project) {

    override fun extractHardCodedString(file: PsiFile): List<String> {
        return emptyList()
    }

}