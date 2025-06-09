package com.liiceberg.utils.files

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter

object FileProcessor {

    fun saveAllFile() = FileDocumentManager.getInstance().saveAllDocuments()

    fun writeFileContent(file: VirtualFile, content: String) {
        ApplicationManager.getApplication().runWriteAction {
            file.getOutputStream(this).use { outputStream ->
                BufferedWriter(OutputStreamWriter(outputStream, Charsets.UTF_8)).use { writer ->
                    writer.write(content)
                }
            }
        }
    }

    fun readFileContent(file: VirtualFile): String {
        return buildString {
            BufferedReader(InputStreamReader(file.inputStream)).use {
                it.forEachLine { line ->
                    append(line)
                    append("\n")
                }
            }
        }
    }

}