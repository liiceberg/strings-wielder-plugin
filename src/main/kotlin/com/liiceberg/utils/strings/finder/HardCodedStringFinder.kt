package com.liiceberg.utils.strings.finder

import com.intellij.psi.PsiFile
import com.liiceberg.utils.files.FileProcessor
import com.liiceberg.utils.files.FileProcessor.readFileContent

abstract class HardCodedStringFinder {

    fun findHardCodedStrings(psiFile: PsiFile): List<String> {

        FileProcessor.saveAllFile()

        val content = readFileContent(psiFile.virtualFile)
        val result = regex().findAll(content)

        return result
                .map { extractHardCodedString(it.value) }
                .filter { shouldInclude(it) }
                .toList()

    }

    protected abstract fun extractHardCodedString(it: String): String

    protected abstract fun regex(): Regex

    protected abstract fun shouldInclude(it: String): Boolean

}