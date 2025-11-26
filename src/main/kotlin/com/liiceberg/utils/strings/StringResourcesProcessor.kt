package com.liiceberg.utils.strings

import com.intellij.openapi.vfs.VirtualFile
import com.liiceberg.ui.entity.HardcodedStringEntity
import com.liiceberg.utils.Constants

class StringResourcesProcessor(private val stringResources: List<String>) {

    fun process(
        hardcodedStrings: List<String>,
        virtualFile: VirtualFile,
    ): List<HardcodedStringEntity> {
        val keysToAddInStringXML = mutableListOf<String>()
        val entries = mutableListOf<HardcodedStringEntity>()

        hardcodedStrings.forEach { str ->
            val stringResourceKey = getKey(str, keysToAddInStringXML)
            keysToAddInStringXML.add(stringResourceKey)
            entries.add(
                HardcodedStringEntity(
                    stringResourceKey,
                    str,
                    true,
                    virtualFile,
                )
            )
        }

        return entries
    }

    private fun getKey(
        originalText: String,
        stringsToAddInStringXMLFile: MutableList<String>,
        repeatCount: Int = 0
    ): String {
        val baseKey = normalizeText(originalText)

        val candidateKey = if (repeatCount == 0) baseKey else "${baseKey}_$repeatCount"

        if (stringResources.contains(candidateKey) || stringsToAddInStringXMLFile.contains(candidateKey)) {
            return getKey(
                baseKey,
                stringsToAddInStringXMLFile,
                repeatCount + 1
            )
        }
        return candidateKey
    }

    private fun normalizeText(originalText: String): String {
        return Constants.RegexTemplates.KEY_GENERATOR_REGEX
            .replace(originalText, "")
            .replace(" ", "_")
            .lowercase()
            .let { cleanedText ->
                if (cleanedText.length <= MAX_KEY_LENGTH) {
                    cleanedText
                } else {
                    val lastUnderscore = cleanedText.substring(0, MAX_KEY_LENGTH).lastIndexOf("_")
                    if (lastUnderscore > 0) {
                        cleanedText.substring(0, lastUnderscore)
                    } else {
                        cleanedText.substring(0, MAX_KEY_LENGTH)
                    }
                }
            }
    }

    private companion object {
        private const val MAX_KEY_LENGTH = 30
    }

}