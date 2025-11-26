package com.liiceberg.utils.module

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.liiceberg.utils.getFacet

class ModuleExplorer(project: Project) {

    private val moduleManager: ModuleManager = ModuleManager.getInstance(project)

    fun getAndroidModules(): List<Module> {
        return moduleManager.modules
            .filter { isAndroidModule(it) }
            .sortedBy { it.name }
    }

    fun getModuleByName(name: String): Module? {
        return moduleManager.findModuleByName(name)
    }

    fun isApplicationModule(module: Module): Boolean {
        val facet = module.getFacet() ?: return false
        return facet.configuration.isAppOrFeature
    }

    private fun isAndroidModule(module: Module): Boolean {
        return module.getFacet() != null
    }
}