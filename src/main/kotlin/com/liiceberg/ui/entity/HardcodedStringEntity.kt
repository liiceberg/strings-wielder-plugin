package com.liiceberg.ui.entity

import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import com.liiceberg.model.PluralResource
import com.liiceberg.strings.SearchUtil
import com.liiceberg.strings.detector.Pattern
import com.liiceberg.strings.translator.SupportedAppLanguage

data class HardcodedStringEntity(
    var key: String,
    var value: String,
    var isSelected: Boolean,
    val virtualFile: VirtualFile,
    val module: Module,
    val sourceValue: String = value,
    val suggestions: MutableSet<SuggestionType> = mutableSetOf(),
    var duplicateOf: List<SearchUtil.SearchResult>? = null,
    var existingResource: SearchUtil.SearchResult? = null,
    var patterns: MutableList<Pattern> = mutableListOf(),
    var pluralForm: PluralResource? = null,
    var sourceLanguage: SupportedAppLanguage? = null,
)

enum class SuggestionType {
    PLURAL,
    TEMPLATE,
    DUPLICATE
}
