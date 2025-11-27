package com.liiceberg.ui.entity

import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile

data class HardcodedStringEntity(
    var key: String,
    var value: String,
    var isSelected: Boolean,
    val virtualFile: VirtualFile,
    val module: Module,
)