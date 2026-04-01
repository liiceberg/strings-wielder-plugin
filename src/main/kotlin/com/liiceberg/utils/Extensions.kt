package com.liiceberg.utils

import com.android.tools.idea.projectsystem.getModuleSystem
import com.intellij.facet.FacetManager
import com.intellij.openapi.module.Module
import org.jetbrains.android.facet.AndroidFacet

fun Module.getFacet() : AndroidFacet? {
    return FacetManager.getInstance(this).getFacetByType(AndroidFacet.ID)
}

fun Module.getAndroidPackageName(): String? {
    return runCatching { getModuleSystem().getPackageName() }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }
}

fun Module.getResourceDependencies(): List<Module> {
    return runCatching { getModuleSystem().getResourceModuleDependencies() }
        .getOrDefault(emptyList())
}
