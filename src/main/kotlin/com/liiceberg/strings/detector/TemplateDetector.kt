package com.liiceberg.strings.detector

object TemplateDetector : Detector {

    val patterns = listOf(
        Regex("\\$[a-zA-Z_][a-zA-Z0-9_]*"), // $var
        Regex("\\$\\{[^}]+}"),              // ${expr}
        Regex("%([0-9]\\$)?[sdf]"),       // %s, %d, %1$s, %2$d
    )

    override fun detect(text: String): List<Pattern> {
        return patterns.flatMap { pattern ->
            pattern.findAll(text).map { match ->
                Pattern(
                    type = PatternType.TEMPLATE,
                    value = match.value,
                    range = match.range,
                    templateFormat = defaultTemplateFormat(match.value),
                )
            }
        }
    }

    private fun defaultTemplateFormat(value: String): String? {
        return when {
            value.startsWith("%") -> when {
                value.endsWith("s") -> "%s"
                value.endsWith("d") -> "%d"
                value.endsWith("f") -> "%f"
                else -> "%s"
            }
            value.startsWith("$") -> "%s"
            else -> null
        }
    }

}
