package com.liiceberg.utils.strings

import com.intellij.openapi.vfs.VirtualFile
import com.liiceberg.ui.entity.HardcodedStringEntity
import com.liiceberg.utils.Constants
import com.liiceberg.utils.LocalStorage
import com.liiceberg.utils.files.FileProcessor.readFileContent

object StringResourcesProcessor {

    fun process(
        hardcodedStrings: List<String>,
        virtualFile: VirtualFile,
        stringXMLFile: VirtualFile
    ): List<HardcodedStringEntity> {
        val keysToAddInStringXML = mutableListOf<String>()
        val prefix = LocalStorage.getData(Constants.Preferences.PREFIX)
        val entries = mutableListOf<HardcodedStringEntity>()

        when {
            virtualFile.name.endsWith(".xml") -> {

                hardcodedStrings.forEach { str ->
                    val stringXMLContent = readFileContent(stringXMLFile)
                    val stringResourceKey = getKey(stringXMLContent, "$prefix$str", keysToAddInStringXML)
                    keysToAddInStringXML.add(stringResourceKey)
                    entries.add(HardcodedStringEntity(stringResourceKey, str, true, virtualFile))
                }

            }

            virtualFile.path.contains("/main/java") -> {
                hardcodedStrings.forEach { str ->
                    val stringXMLContent = readFileContent(stringXMLFile)
                    val stringResourceKey = getKey(stringXMLContent, "$prefix$str", keysToAddInStringXML)
                    keysToAddInStringXML.add(stringResourceKey)
                    entries.add(
                        HardcodedStringEntity(
                            stringResourceKey,
                            str,
                            true,
                            virtualFile,
                            Constants.javaExtractTemplate
                        )
                    )
                }
            }
        }
        return entries
    }

    private fun getKey(
        stringsXMLFileContent: String,
        originalText: String,
        stringsToAddInStringXMLFile: MutableList<String>,
        repeatCount: Int = 0
    ): String {
        val baseKey = Constants.RegexTemplates.KEY_GENERATOR_REGEX.replace(originalText, "").replace(" ", "_")

        val candidateKey = if (repeatCount == 0) baseKey else "${baseKey}_$repeatCount"

        if (stringsXMLFileContent.contains(candidateKey) || stringsToAddInStringXMLFile.contains(candidateKey)) {
            return getKey(
                stringsXMLFileContent,
                baseKey,
                stringsToAddInStringXMLFile,
                repeatCount + 1
            )
        }
        return candidateKey
    }

}