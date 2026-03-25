package com.liiceberg.model

import com.intellij.openapi.vfs.VirtualFile

data class FileContent(
    val file: VirtualFile,
    val strings: List<String>,
)
