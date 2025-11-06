package com.liiceberg.utils.module

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.isFile
import org.jetbrains.kotlin.idea.util.isKotlinFileType

object ModuleFileFinder {

    fun getModuleKotlinFiles(module: Module): List<VirtualFile> {
        return getModuleSourceDirectories(module)
            .flatMap {
                findKotlinFilesInDirectory(it)
            }
    }

    fun getModuleStringFiles(module: Module): List<VirtualFile> {
        return getModuleResourceDirectories(module)
            .flatMap { findStringFilesInDirectory(it) }
    }

    private fun getModuleSourceDirectories(module: Module): Array<out VirtualFile> {
        return ModuleRootManager.getInstance(module).sourceRoots
    }

    private fun getModuleResourceDirectories(module: Module): List<VirtualFile> {
        val rootManager = ModuleRootManager.getInstance(module)
        return rootManager.contentRoots.flatMap { root ->
            listOfNotNull(
                VfsUtil.findRelativeFile(root, "src/main/res"),
            )
        }.filter { it != null && it.exists() }
    }

    private fun findKotlinFilesInDirectory(directory: VirtualFile): List<VirtualFile> {
        return VfsUtil.collectChildrenRecursively(directory)
            .filter {
                it.isFile && it.isKotlinFileType()
            }
    }

    private fun findStringFilesInDirectory(directory: VirtualFile): List<VirtualFile> {
        return VfsUtil.collectChildrenRecursively(directory)
            .filter { it.name == "strings.xml" && it.path.contains("values") }
    }
}