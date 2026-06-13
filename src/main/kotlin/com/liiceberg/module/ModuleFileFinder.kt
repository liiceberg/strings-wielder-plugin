package com.liiceberg.module

import com.android.tools.idea.ui.resourcemanager.importer.getOrCreateDefaultResDirectory
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.isFile
import com.liiceberg.utils.Constants.STRING_RESOURCE_FILE
import com.liiceberg.utils.Constants.STRING_RESOURCE_FILE_DIR
import com.liiceberg.utils.getFacet
import org.jetbrains.kotlin.idea.core.util.toVirtualFile
import org.jetbrains.kotlin.idea.util.isKotlinFileType
import java.io.File

object ModuleFileFinder {

    fun getModuleKotlinFiles(module: Module): List<VirtualFile> {
        return getModuleSourceDirectories(module)
            .flatMap {
                findKotlinFilesInDirectory(it)
            }
    }

    fun getModuleStringFiles(module: Module): List<VirtualFile> {
        val res = getModuleResourceDirectory(module)?.toVirtualFile() ?: return emptyList()
        return findStringFilesInDirectory(res)
    }

    private fun getModuleSourceDirectories(module: Module): Array<out VirtualFile> {
        return ModuleRootManager.getInstance(module).sourceRoots
    }

    private fun getModuleResourceDirectory(module: Module): File? {
        val facet = module.getFacet() ?: return null
        return getOrCreateDefaultResDirectory(facet)
    }

    private fun findKotlinFilesInDirectory(directory: VirtualFile): List<VirtualFile> {
        return VfsUtil.collectChildrenRecursively(directory)
            .filter {
                it.isFile && it.isKotlinFileType()
            }
    }

    private fun findStringFilesInDirectory(directory: VirtualFile): List<VirtualFile> {
        return VfsUtil.collectChildrenRecursively(directory)
            .filter { it.path.contains(STRING_RESOURCE_FILE_DIR)  && it.name == STRING_RESOURCE_FILE}
    }
}
