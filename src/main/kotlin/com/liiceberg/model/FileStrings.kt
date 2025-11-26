package com.liiceberg.model

import com.intellij.openapi.vfs.VirtualFile

data class FileStrings(
    val file: VirtualFile,
    val strings: List<String>,
)
