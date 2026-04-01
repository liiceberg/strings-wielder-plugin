package com.liiceberg.strings.finder

import com.android.resources.ResourceType
import com.android.tools.idea.ui.resourcemanager.model.ResourceSection
import com.android.tools.idea.ui.resourcemanager.model.getDependentModuleResources
import com.android.tools.idea.ui.resourcemanager.model.getModuleResources
import com.intellij.openapi.module.Module
import com.liiceberg.utils.getFacet

object StringResourceFinder {

    fun getAccessibleResourceNames(module: Module): Set<String> {
        return buildSet {
            addAll(extractNames(getModuleStrings(module)))
            getDependentStrings(module).forEach { addAll(extractNames(it)) }
            addAll(extractNames(getModulePlurals(module)))
            getDependentPlurals(module).forEach { addAll(extractNames(it)) }
        }
    }

    private fun getModuleStrings(module: Module): ResourceSection? {
        return getModuleResources(module, ResourceType.STRING)
    }

    private fun getDependentStrings(module: Module): List<ResourceSection> {
        return getDependentModuleResources(module, ResourceType.STRING)
    }

    private fun getModulePlurals(module: Module): ResourceSection? {
        return getModuleResources(module, ResourceType.PLURALS)
    }

    private fun getDependentPlurals(module: Module): List<ResourceSection> {
        return getDependentModuleResources(module, ResourceType.PLURALS)
    }

    private fun getModuleResources(
        module: Module,
        resourceType: ResourceType,
    ): ResourceSection? {
        val facet = module.getFacet() ?: return null
        return getModuleResources(facet, resourceType, emptyList())
    }

    private fun getDependentModuleResources(
        module: Module,
        resourceType: ResourceType,
    ): List<ResourceSection> {
        val facet = module.getFacet() ?: return emptyList()
        return getDependentModuleResources(facet, resourceType, emptyList())
    }

    private fun extractNames(section: ResourceSection?): Set<String> {
        return section?.assetSets
            ?.map { it.name }
            ?.toSet()
            ?: emptySet()
    }
}
