package com.liiceberg.utils

import com.android.tools.idea.projectsystem.getModuleSystem
import com.intellij.facet.FacetManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.Module
import com.intellij.psi.xml.XmlFile
import com.liiceberg.strings.translator.ResourceDirectoryLanguage
import com.liiceberg.strings.translator.SupportedAppLanguage
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.resolve.ImportPath

fun Module.getFacet(): AndroidFacet? {
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

fun KtFile.addImports(imports: List<String>, ktPsiFactory: KtPsiFactory) {
    WriteCommandAction.runWriteCommandAction(project) {
        val existingImports = this.importDirectives.map { it.text.split(' ').last() }
        imports.forEach { currentImport ->
            if (currentImport !in existingImports) {
                val newImport = ktPsiFactory.createImportDirective(
                    ImportPath.fromString(currentImport)
                )
                val importList = this.importList
                if (importList != null) {
                    importList.addAfter(newImport, importList.lastChild)
                } else {
                    val packageDirective = this.packageDirective
                    if (packageDirective != null) {
                        this.addAfter(newImport, packageDirective)
                        this.addAfter(ktPsiFactory.createNewLine(), packageDirective)
                    } else {
                        this.addAfter(newImport, null)
                    }
                }
            }
        }
    }
}

fun getSavedBaseLanguage(): SupportedAppLanguage {
    val savedName = LocalStorage.getData(Constants.Preferences.BASE_LANGUAGE)
    return savedName
        ?.let { runCatching { SupportedAppLanguage.valueOf(it) }.getOrNull() }
        ?: SupportedAppLanguage.ENGLISH
}

fun XmlFile.resolveDirectoryLanguage(): ResourceDirectoryLanguage {
    val directoryName = this.virtualFile.parent?.name ?: return ResourceDirectoryLanguage.Unsupported
    return SupportedAppLanguage.resolveResourceDirectory(directoryName)
}
