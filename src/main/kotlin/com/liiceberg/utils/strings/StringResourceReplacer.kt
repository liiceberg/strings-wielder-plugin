package com.liiceberg.utils.strings

import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.VirtualFile
import com.liiceberg.ui.entity.HardcodedStringEntity
import com.liiceberg.utils.Constants
import com.liiceberg.utils.LocalStorage
import com.liiceberg.utils.appendIfNotContains
import com.liiceberg.utils.files.FileProcessor

class StringResourceReplacer(private val entries: List<HardcodedStringEntity>, private val stringXMLFile: VirtualFile) {

    private val stringsToAddInStringXMLFile = StringBuilder()

    fun replace() {
        ApplicationManager.getApplication().runReadAction {
            entries.filter { it.isSelected }.forEach {
                if (it.virtualFile.fileType == XmlFileType.INSTANCE) {
                    replaceHardCodedXMLStrings(it)
                } else {
                    var fileContent = FileProcessor.readFileContent(it.virtualFile)
                    val replacementMethod = determineReplacementMethod(fileContent, it.key, it.value)
                    val resourcesImport = "import ${LocalStorage.getData(Constants.Preferences.IMPORT_PACKAGE)}.R"

                    fileContent = when (replacementMethod.first) {
                        ReplacementMethod.COMPOSABLE -> addImports(
                            listOf(
                                resourcesImport,
                                "import androidx.compose.ui.res.stringResource"
                            ),
                            fileContent
                        )

                        ReplacementMethod.APPLICATION -> addImports(listOf(resourcesImport), fileContent)
                        else -> fileContent
                    }

                    fileContent = fileContent.replaceFirst(
                        "\"${it.value}\"",
                        replacementMethod.second
                    )
                    stringsToAddInStringXMLFile.appendIfNotContains("\n\t<string name=\"${it.key}\">${it.value}</string>")
                    FileProcessor.writeFileContent(it.virtualFile, fileContent)
                }
            }
        }

        updateStringXMLFile(stringXMLFile, stringsToAddInStringXMLFile)
    }

    private fun updateStringXMLFile(stringXMLFile: VirtualFile, stringsToAddInStringXMLFile: StringBuilder) {
        val stringXMLFileContent = FileProcessor.readFileContent(stringXMLFile)
        val stringBuilder = StringBuilder(stringXMLFileContent.substringBeforeLast("</resources>"))
        stringBuilder.append(stringsToAddInStringXMLFile).append("\n</resources>")
        FileProcessor.writeFileContent(stringXMLFile, stringBuilder.toString())
    }

    private fun replaceHardCodedXMLStrings(
        hardCodedStringEntry: HardcodedStringEntity,
    ) {
        hardCodedStringEntry.virtualFile.let {
            var content = FileProcessor.readFileContent(it)
            when {
                content.contains("android:text=\"${hardCodedStringEntry.value}\"") -> {
                    content = content.replaceFirst(
                        "android:text=\"${hardCodedStringEntry.value}\"",
                        "android:text=\"@string/${hardCodedStringEntry.key}\""
                    )
                    stringsToAddInStringXMLFile.appendIfNotContains("\n\t<string name=\"${hardCodedStringEntry.key}\">${hardCodedStringEntry.value}</string>")
                }

                content.contains("android:title=\"${hardCodedStringEntry.value}\"") -> {
                    content = content.replaceFirst(
                        "android:title=\"${hardCodedStringEntry.value}\"",
                        "android:title=\"@string/${hardCodedStringEntry.key}\""
                    )
                    stringsToAddInStringXMLFile.appendIfNotContains("\n\t<string name=\"${hardCodedStringEntry.key}\">${hardCodedStringEntry.value}</string>")
                }

                content.contains("android:hint=\"${hardCodedStringEntry.value}\"") -> {
                    content = content.replaceFirst(
                        "android:hint=\"${hardCodedStringEntry.value}\"",
                        "android:hint=\"@string/${hardCodedStringEntry.key}\""
                    )
                    stringsToAddInStringXMLFile.appendIfNotContains("\n\t<string name=\"${hardCodedStringEntry.key}\">${hardCodedStringEntry.value}</string>")
                }

                content.contains("android:subTitle=\"${hardCodedStringEntry.value}\"") -> {
                    content = content.replaceFirst(
                        "android:subTitle=\"${hardCodedStringEntry.value}\"",
                        "android:subTitle=\"@string/${hardCodedStringEntry.key}\""
                    )
                    stringsToAddInStringXMLFile.appendIfNotContains("\n\t<string name=\"${hardCodedStringEntry.key}\">${hardCodedStringEntry.value}</string>")
                }
            }
            if (it.path.contains("/res/layout")
                || it.path.contains("/res/menu")
            ) {
                FileProcessor.writeFileContent(it, content)
            }
        }
    }


    private fun determineReplacementMethod(
        fileContent: String,
        stringKey: String,
        stringValue: String,
    ): Pair<ReplacementMethod, String> {
        val stringIndex = fileContent.indexOf("\"$stringValue\"")

        return when {
            isInsideComposable(fileContent, stringIndex) -> {
                Pair(ReplacementMethod.COMPOSABLE, "stringResource(R.string.$stringKey)")
            }

            isInsideFragment(fileContent) -> {
                Pair(ReplacementMethod.FRAGMENT, "requireContext().getString(R.string.$stringKey)")
            }

            isInsideActivity(fileContent) -> {
                Pair(ReplacementMethod.ACTIVITY, "getString(R.string.$stringKey)")
            }

            isInsideApplication(fileContent) -> {
                Pair(ReplacementMethod.APPLICATION, "applicationContext.getString(R.string.$stringKey)")
            }

            else -> {
                getViewVariableIfExist(fileContent, stringIndex)?.let {
                    return Pair(ReplacementMethod.VIEW_VARIABLE, "$it.context.getString(R.string.$stringKey)")
                }
                getContextVariableIfExist(fileContent, stringIndex)?.let {
                    return Pair(ReplacementMethod.CONTEXT_VARIABLE, "$it.getString(R.string.$stringKey)")
                }
                Pair(ReplacementMethod.OTHER, "context.getString(R.string.$stringKey)")
            }
        }
    }


    private fun isInsideComposable(fileContent: String, position: Int): Boolean {
        val currentFunStart = fileContent.lastIndexOf("fun ", position)
        if (currentFunStart == -1) return false
        val prevFunStart = fileContent.lastIndexOf("fun ", currentFunStart - 1).coerceAtLeast(0)

        return fileContent.substring(prevFunStart, currentFunStart).contains("@Composable")
    }

    private fun isInsideFragment(fileContent: String): Boolean {
        return fileContent.contains("class") && (fileContent.contains("Fragment") ||
                fileContent.contains("requireContext()") ||
                fileContent.contains("requireActivity()"))
    }

    private fun isInsideActivity(fileContent: String): Boolean {
        return fileContent.contains("class") &&
                (fileContent.contains("Activity") ||
                        fileContent.contains("AppCompatActivity"))
    }

    private fun isInsideApplication(fileContent: String): Boolean {
        return fileContent.contains("class") && (fileContent.contains("Application") && fileContent.contains("App"))
    }


    private fun getContextVariableIfExist(fileContent: String, position: Int): String? {
        val scope = getCurrentScope(fileContent, position)
        return when {
            scope.contains("context") -> "context"
            scope.contains("getContext()") -> "getContext()"
            scope.contains("applicationContext") -> "applicationContext"
            else -> null
        }
    }

    private fun getViewVariableIfExist(fileContent: String, position: Int): String? {
        val scope = getCurrentScope(fileContent, position)
        return when {
            scope.contains("view") -> "view"
            scope.contains("itemView") -> "itemView"
            scope.contains("binding") -> "binding.root"
            else -> null
        }
    }

    private fun getCurrentScope(fileContent: String, position: Int): String {
        val currentFunStart = fileContent.lastIndexOf("fun ", position)
        val scopeEnd = fileContent.indexOf("}", position)
        return if (currentFunStart != -1 && scopeEnd != -1) {
            fileContent.substring(currentFunStart, scopeEnd)
        } else ""
    }

    private fun addImports(imports: List<String>, content: String): String {
        imports.forEach { import ->
            if (content.contains(import).not()) {
                return content.replaceFirst("\n", "\n\n$import")
            }
        }
        return content
    }

    enum class ReplacementMethod {
        COMPOSABLE, FRAGMENT, ACTIVITY, APPLICATION, VIEW_VARIABLE, CONTEXT_VARIABLE, OTHER
    }

}

