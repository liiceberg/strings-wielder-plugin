package com.liiceberg.strings

import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import com.liiceberg.strings.translator.LanguageDetector
import com.liiceberg.strings.translator.SupportedAppLanguage
import com.liiceberg.strings.translator.Translator
import com.liiceberg.ui.entity.HardcodedStringEntity
import com.liiceberg.utils.Constants
import kotlinx.coroutines.runBlocking

class StringResourcesProcessor(private val stringResources: Set<String>) {
    private val translator = Translator()

    fun process(
        hardcodedStrings: List<String>,
        virtualFile: VirtualFile,
        module: Module,
    ): List<HardcodedStringEntity> {
        val keysToAddInStringXML = mutableListOf<String>()
        val entries = mutableListOf<HardcodedStringEntity>()

        hardcodedStrings.forEach { str ->
            val sourceLanguage = LanguageDetector.detect(str)
            val stringResourceKey = getKey(str, sourceLanguage, keysToAddInStringXML)
            keysToAddInStringXML.add(stringResourceKey)
            entries.add(
                HardcodedStringEntity(
                    stringResourceKey,
                    str,
                    true,
                    virtualFile,
                    module,
                    sourceLanguage = sourceLanguage,
                )
            )
        }

        return entries
    }

    private fun getKey(
        originalText: String,
        sourceLanguage: SupportedAppLanguage?,
        stringsToAddInStringXMLFile: MutableList<String>,
        repeatCount: Int = 0
    ): String {
        val englishText = translateToEnglishIfNeeded(originalText, sourceLanguage)
        val baseKey = normalizeText(englishText)

        val candidateKey = if (repeatCount == 0) baseKey else "${baseKey}_$repeatCount"

        if (stringResources.contains(candidateKey) || stringsToAddInStringXMLFile.contains(candidateKey)) {
            return getKey(
                englishText,
                SupportedAppLanguage.ENGLISH,
                stringsToAddInStringXMLFile,
                repeatCount + 1
            )
        }
        return candidateKey
    }

    private fun translateToEnglishIfNeeded(
        originalText: String,
        sourceLanguage: SupportedAppLanguage?,
    ): String {
        if (!shouldTranslateToEnglish(originalText, sourceLanguage)) {
            return originalText
        }
        val detectedLanguage = sourceLanguage ?: return originalText

        return runCatching {
            runBlocking {
                translator.translate(
                    text = originalText,
                    sourceLanguage = detectedLanguage,
                    targetLanguage = SupportedAppLanguage.ENGLISH,
                )
            }
        }.getOrDefault(originalText)
    }

    private fun shouldTranslateToEnglish(
        originalText: String,
        sourceLanguage: SupportedAppLanguage?,
    ): Boolean {
        if (sourceLanguage == null || sourceLanguage == SupportedAppLanguage.ENGLISH) {
            return false
        }

        val letters = originalText.filter { it.isLetter() }
        if (letters.isEmpty()) return false

        return letters.any {
            Character.UnicodeScript.of(it.code) != Character.UnicodeScript.LATIN
        }
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
