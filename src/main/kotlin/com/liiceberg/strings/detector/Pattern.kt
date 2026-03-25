package com.liiceberg.strings.detector

data class Pattern(
    val type: PatternType,
    val value: String,
    val range: IntRange,
)

enum class PatternType {
    PLURAL, TEMPLATE
}