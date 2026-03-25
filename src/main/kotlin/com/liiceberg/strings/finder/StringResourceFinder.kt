package com.liiceberg.strings.finder

import com.android.resources.ResourceType
import com.android.tools.idea.ui.resourcemanager.model.ResourceSection
import com.android.tools.idea.ui.resourcemanager.model.getModuleResources
import com.intellij.openapi.module.Module
import com.liiceberg.utils.getFacet

object StringResourceFinder {

    //    Only strings from default strings.xml file
    fun getModuleStrings(module: Module): ResourceSection? {
        val facet = module.getFacet() ?: return null
//        for resources from other modules getDependentModuleResources()
        return getModuleResources(facet, ResourceType.STRING, emptyList())
    }

    fun getModulePlurals(module: Module): ResourceSection? {
        val facet = module.getFacet() ?: return null
        return getModuleResources(facet, ResourceType.PLURALS, emptyList())
    }
}