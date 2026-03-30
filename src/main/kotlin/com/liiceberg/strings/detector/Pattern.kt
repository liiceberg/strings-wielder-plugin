package com.liiceberg.strings.detector

data class Pattern(
    val type: PatternType,
    val value: String,
    val range: IntRange,
    var templateFormat: String? = null,
)

enum class PatternType {
    PLURAL, TEMPLATE
}
