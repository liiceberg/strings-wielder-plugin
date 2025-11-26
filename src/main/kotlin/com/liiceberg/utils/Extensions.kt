package com.liiceberg.utils

import com.intellij.facet.FacetManager
import com.intellij.openapi.module.Module
import org.jetbrains.android.facet.AndroidFacet

fun Module.getFacet() : AndroidFacet? {
    return FacetManager.getInstance(this).getFacetByType(AndroidFacet.ID)
}