package com.liiceberg.utils.strings.finder

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.liiceberg.utils.files.FileProcessor

abstract class HardCodedStringFinder(project: Project) {

    private val psiManager = PsiManager.getInstance(project)

    fun findHardCodedStrings(virtualFile: VirtualFile): List<String> {
        FileProcessor.saveAllFile()

        psiManager.findFile(virtualFile)?.let { psiFile ->
            if (psiFile.isWritable) {
                return extractHardCodedString(psiFile)
            }
        }
        return emptyList()
    }

    protected abstract fun extractHardCodedString(file: PsiFile): List<String>

}