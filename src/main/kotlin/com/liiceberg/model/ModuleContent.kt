package com.liiceberg.model

import com.intellij.openapi.module.Module

data class ModuleContent(
    val module: Module,
    val strings: List<FileContent>,
)