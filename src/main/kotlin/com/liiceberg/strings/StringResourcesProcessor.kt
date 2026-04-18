package com.liiceberg.strings

import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.vfs.VirtualFile
import com.liiceberg.strings.detector.TemplateDetector
import com.liiceberg.strings.translator.LanguageDetector
import com.liiceberg.strings.translator.SupportedAppLanguage
import com.liiceberg.strings.translator.Translator
import com.liiceberg.ui.entity.HardcodedStringEntity
import com.liiceberg.utils.Constants

class StringResourcesProcessor(private val stringResources: Set<String>) {
    private val translator = Translator()

    suspend fun process(
        hardcodedStrings: List<String>,
        virtualFile: VirtualFile,
        module: Module,
        onTranslationStarted: (String) -> Unit = {},
    ): List<HardcodedStringEntity> {
        val keysToAddInStringXML = mutableListOf<String>()
        val entries = mutableListOf<HardcodedStringEntity>()

        hardcodedStrings.forEach { str ->
            ProgressManager.checkCanceled()
            val sourceLanguage = LanguageDetector.detect(str)
            val stringResourceKey = if (containsLetters(str)) {
                getKey(str, sourceLanguage, keysToAddInStringXML, onTranslationStarted = onTranslationStarted)
            } else ""
            if (stringResourceKey.isNotBlank()) {
                keysToAddInStringXML.add(stringResourceKey)
            }
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

    private suspend fun getKey(
        originalText: String,
        sourceLanguage: SupportedAppLanguage?,
        stringsToAddInStringXMLFile: MutableList<String>,
        repeatCount: Int = 0,
        onTranslationStarted: (String) -> Unit = {},
    ): String {
        val textForKey = stripTemplateParameters(originalText)
        if (!containsLetters(textForKey)) {
            return ""
        }

        val englishText = translateToEnglishIfNeeded(textForKey, sourceLanguage, onTranslationStarted) ?: return ""
        val baseKey = normalizeText(englishText)
        if (baseKey.isBlank()) {
            return ""
        }

        val candidateKey = if (repeatCount == 0) baseKey else "${baseKey}_$repeatCount"

        if (stringResources.contains(candidateKey) || stringsToAddInStringXMLFile.contains(candidateKey)) {
            return getKey(
                englishText,
                SupportedAppLanguage.ENGLISH,
                stringsToAddInStringXMLFile,
                repeatCount + 1,
                onTranslationStarted,
            )
        }
        return candidateKey
    }

    private suspend fun translateToEnglishIfNeeded(
        originalText: String,
        sourceLanguage: SupportedAppLanguage?,
        onTranslationStarted: (String) -> Unit,
    ): String? {
        if (!shouldTranslateToEnglish(originalText, sourceLanguage)) {
            return originalText
        }
        val detectedLanguage = sourceLanguage ?: return originalText

        onTranslationStarted(originalText)
        return runCatching {
            translator.translate(
                text = originalText,
                sourceLanguage = detectedLanguage,
                targetLanguage = SupportedAppLanguage.ENGLISH,
            )
        }.getOrNull()
    }

    private fun shouldTranslateToEnglish(
        originalText: String,
        sourceLanguage: SupportedAppLanguage?,
    ): Boolean {
        if (sourceLanguage == null || sourceLanguage == SupportedAppLanguage.ENGLISH) {
            return false
        }
        val letters = originalText.filter { it.isLetter() }
        return letters.any {
            Character.UnicodeScript.of(it.code) != Character.UnicodeScript.LATIN
        }
    }

    private fun containsLetters(originalText: String): Boolean {
        return originalText.any { it.isLetter() }
    }

    private fun stripTemplateParameters(originalText: String): String {
        return TemplateDetector.patterns
            .fold(originalText) { acc, pattern -> pattern.replace(acc, " ") }
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun normalizeText(originalText: String): String {
        return Constants.RegexTemplates.KEY_GENERATOR_REGEX
            .replace(originalText, "")
            .replace(Regex("\\s+"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
            .lowercase()
            .let { cleanedText ->
                if (cleanedText.isBlank()) {
                    return ""
                }
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
