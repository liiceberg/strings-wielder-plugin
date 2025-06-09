package com.liiceberg.utils

fun String.toRegexOrNull(): Regex? = try {
    Regex(this)
} catch (e: Exception) {
    null
}

fun StringBuilder.appendIfNotContains(str: String) {
    if (this.contains(str).not()) {
        this.append(str)
    }
}