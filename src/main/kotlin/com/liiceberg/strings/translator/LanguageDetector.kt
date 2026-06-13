package com.liiceberg.strings.translator

import com.github.pemistahl.lingua.api.LanguageDetectorBuilder

object LanguageDetector {

    private val detector = LanguageDetectorBuilder
        .fromLanguages(*SupportedAppLanguage.detectableLanguages)
        .build()

    fun detect(text: String): SupportedAppLanguage? {
        if (text.isBlank()) return null
        return SupportedAppLanguage.fromLingua(detector.detectLanguageOf(text))
    }
}
