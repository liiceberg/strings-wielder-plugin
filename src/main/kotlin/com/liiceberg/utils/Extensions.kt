package com.liiceberg.utils

import com.intellij.facet.FacetManager
import com.intellij.openapi.module.Module
import org.jetbrains.android.facet.AndroidFacet

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

fun Module.getFacet() : AndroidFacet? {
    return FacetManager.getInstance(this).getFacetByType(AndroidFacet.ID)
}