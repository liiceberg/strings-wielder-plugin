package com.liiceberg.utils

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope

object ApplicationUtil {

    fun findApplicationId(project: Project): String? {
        val gradleFiles = FilenameIndex.getVirtualFilesByName("build.gradle", GlobalSearchScope.projectScope(project)) +
                FilenameIndex.getVirtualFilesByName("build.gradle.kts", GlobalSearchScope.projectScope(project))

        val targetFile = gradleFiles.firstOrNull { it.path.contains("/app/") } ?: return null
        val psiFile = PsiManager.getInstance(project).findFile(targetFile) ?: return null

        val text = psiFile.text

        val regex = Regex("""applicationId\s*(=|\s)\s*["']([^"']+)["']""")
        val match = regex.find(text)
        return match?.groupValues?.get(2)
    }
}