package com.liiceberg.strings.detector

object TemplateDetector : Detector {

    private val patterns = listOf(
        Regex("\\$[a-zA-Z_][a-zA-Z0-9_]*"), // $var
        Regex("\\$\\{[^}]+}"),              // ${expr}
        Regex("%([0-9]\\$)?[sdf]"),       // %s, %d, %1$s, %2$d
    )

    override fun detect(text: String): List<Pattern> {
        return patterns.flatMap { pattern ->
            pattern.findAll(text).map { match ->
                Pattern(PatternType.TEMPLATE, match.value, match.range)
            }
        }
    }

}