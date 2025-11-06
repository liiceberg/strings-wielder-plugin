package com.liiceberg.utils.module

import com.android.tools.idea.projectsystem.getManifestFiles
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.project.Project
import  com.intellij.openapi.module.Module
import com.liiceberg.utils.getFacet
import org.jetbrains.plugins.gradle.config.isGradleFile

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

    fun findAndroidManifest(module: Module): List<VirtualFile>? {
        return module.getFacet()?.getManifestFiles()
    }

    fun findModuleGradleFile(module: Module): List<VirtualFile> {
        return ModuleRootManager.getInstance(module).sourceRoots.filter {
            it.isGradleFile()
        }
    }

    private fun isAndroidModule(module: Module): Boolean {
        return module.getFacet() != null
    }
}