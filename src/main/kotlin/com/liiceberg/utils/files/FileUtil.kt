package com.liiceberg.utils.files

import com.intellij.ide.highlighter.XmlFileType
import com.intellij.lang.Language
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.liiceberg.utils.Constants

object FileUtil {

    private val javaFileType = Language.findLanguageByID("JAVA")?.associatedFileType
    private val kotlinFileType = Language.findLanguageByID("kotlin")?.associatedFileType

    fun getAllLayoutXMLFiles(project: Project): Collection<VirtualFile> {
        return FileTypeIndex
            .getFiles(XmlFileType.INSTANCE, GlobalSearchScope.allScope(project))
            .filter { it.path.contains(Constants.Path.RES_LAYOUT_PATH) || it.path.contains(Constants.Path.RES_MENU_PATH) }
    }

    fun getAllSrcJavaFiles(project: Project): Collection<VirtualFile>? {
        return javaFileType?.let {
            FileTypeIndex
                .getFiles(it, GlobalSearchScope.allScope(project))
                .filter { file -> file.path.contains(Constants.Path.SOURCE_CODE_PATH) }
        }
    }

    fun getAllSrcKotlinFiles(project: Project): Collection<VirtualFile>? {
        return kotlinFileType?.let {
            FileTypeIndex
                .getFiles(it, GlobalSearchScope.allScope(project))
                .filter { file -> file.path.contains(Constants.Path.SOURCE_CODE_PATH) }
        }
    }

    fun getStringXMLFiles(project: Project): VirtualFile {
        return FileTypeIndex
            .getFiles(XmlFileType.INSTANCE, GlobalSearchScope.allScope(project))
            .first { file -> file.path.contains(Constants.Path.STRINGS_XML_PATH) }
    }

}