package com.liiceberg.strings.translator

import com.github.pemistahl.lingua.api.Language
import java.util.Locale

enum class SupportedAppLanguage(
    val displayName: String,
    val mbartCode: String,
    private val androidQualifier: String,
    val linguaLanguage: Language?,
    val isTranslatable: Boolean = true,
) {
    NON_TRANSLATABLE("Non-translatable", "", "", null, false),
    AFRIKAANS("Afrikaans", "af_ZA", "af", Language.AFRIKAANS),
    ARABIC("Arabic", "ar_AR", "ar", Language.ARABIC),
    AZERBAIJANI("Azerbaijani", "az_AZ", "az", Language.AZERBAIJANI),
    BENGALI("Bengali", "bn_IN", "bn", Language.BENGALI),
    CHINESE_SIMPLIFIED("Chinese (Simplified)", "zh_CN", "zh-rCN", Language.CHINESE),
    CROATIAN("Croatian", "hr_HR", "hr", Language.CROATIAN),
    CZECH("Czech", "cs_CZ", "cs", Language.CZECH),
    DUTCH("Dutch", "nl_XX", "nl", Language.DUTCH),
    ENGLISH("English", "en_XX", "en", Language.ENGLISH),
    ESTONIAN("Estonian", "et_EE", "et", Language.ESTONIAN),
    FINNISH("Finnish", "fi_FI", "fi", Language.FINNISH),
    FRENCH("French", "fr_XX", "fr", Language.FRENCH),
    GEORGIAN("Georgian", "ka_GE", "ka", Language.GEORGIAN),
    GERMAN("German", "de_DE", "de", Language.GERMAN),
    GUJARATI("Gujarati", "gu_IN", "gu", Language.GUJARATI),
    HEBREW("Hebrew", "he_IL", "he", Language.HEBREW),
    HINDI("Hindi", "hi_IN", "hi", Language.HINDI),
    INDONESIAN("Indonesian", "id_ID", "id", Language.INDONESIAN),
    ITALIAN("Italian", "it_IT", "it", Language.ITALIAN),
    JAPANESE("Japanese", "ja_XX", "ja", Language.JAPANESE),
    KAZAKH("Kazakh", "kk_KZ", "kk", Language.KAZAKH),
    KOREAN("Korean", "ko_KR", "ko", Language.KOREAN),
    LATVIAN("Latvian", "lv_LV", "lv", Language.LATVIAN),
    LITHUANIAN("Lithuanian", "lt_LT", "lt", Language.LITHUANIAN),
    MACEDONIAN("Macedonian", "mk_MK", "mk", Language.MACEDONIAN),
    MARATHI("Marathi", "mr_IN", "mr", Language.MARATHI),
    PERSIAN("Persian", "fa_IR", "fa", Language.PERSIAN),
    POLISH("Polish", "pl_PL", "pl", Language.POLISH),
    PORTUGUESE("Portuguese", "pt_XX", "pt", Language.PORTUGUESE),
    ROMANIAN("Romanian", "ro_RO", "ro", Language.ROMANIAN),
    RUSSIAN("Russian", "ru_RU", "ru", Language.RUSSIAN),
    SLOVENIAN("Slovenian", "sl_SI", "sl", Language.SLOVENE),
    SPANISH("Spanish", "es_XX", "es", Language.SPANISH),
    SWAHILI("Swahili", "sw_KE", "sw", Language.SWAHILI),
    SWEDISH("Swedish", "sv_SE", "sv", Language.SWEDISH),
    TAGALOG("Tagalog", "tl_XX", "tl", Language.TAGALOG),
    TAMIL("Tamil", "ta_IN", "ta", Language.TAMIL),
    TELUGU("Telugu", "te_IN", "te", Language.TELUGU),
    THAI("Thai", "th_TH", "th", Language.THAI),
    TURKISH("Turkish", "tr_TR", "tr", Language.TURKISH),
    UKRAINIAN("Ukrainian", "uk_UA", "uk", Language.UKRAINIAN),
    URDU("Urdu", "ur_PK", "ur", Language.URDU),
    VIETNAMESE("Vietnamese", "vi_VN", "vi", Language.VIETNAMESE),
    XHOSA("Xhosa", "xh_ZA", "xh", Language.XHOSA);

    val androidValuesDirectoryName: String = if (androidQualifier.isBlank()) "values" else "values-$androidQualifier"

    override fun toString(): String = displayName

    fun matchesQualifier(languageCode: String): Boolean {
        return languageCode.lowercase(Locale.ROOT) == androidQualifier.substringBefore('-')
    }

    companion object {
        private val byLingua = entries
            .mapNotNull { language -> language.linguaLanguage?.let { it to language } }
            .toMap()

        val dropdownValues: List<SupportedAppLanguage> = listOf(NON_TRANSLATABLE) +
            entries
                .filter { it != NON_TRANSLATABLE }
                .sortedBy { it.displayName }

        val baseLanguageValues: List<SupportedAppLanguage> = entries
            .filter { it.isTranslatable }
            .sortedBy { it.displayName }

        val detectableLanguages: Array<Language> = entries
            .filter { it.isTranslatable }
            .mapNotNull { it.linguaLanguage }
            .distinct()
            .toTypedArray()

        fun fromLingua(language: Language): SupportedAppLanguage? = byLingua[language]

        fun resolveResourceDirectory(directoryName: String): ResourceDirectoryLanguage = when {
            directoryName == DEFAULT_VALUES_DIRECTORY_NAME -> ResourceDirectoryLanguage.Default
            !directoryName.startsWith(DEFAULT_VALUES_DIRECTORY_NAME) -> ResourceDirectoryLanguage.Unsupported
            else -> parseLocaleQualifier(directoryName.removePrefix("$DEFAULT_VALUES_DIRECTORY_NAME-"))
                ?.let(ResourceDirectoryLanguage::Known)
                ?: ResourceDirectoryLanguage.Unsupported
        }

        private fun parseLocaleQualifier(qualifier: String): SupportedAppLanguage? {
            if (qualifier.isBlank()) return null

            if (qualifier.startsWith(BCP47_PREFIX)) {
                val parts = qualifier.split('+')
                val languageCode = parts.getOrNull(1) ?: return null
                return findByQualifier(languageCode)
            }

            val segments = qualifier.split('-')
            val languageCode = segments.firstOrNull()?.takeIf { it.length == 2 } ?: return null
            return findByQualifier(languageCode)
        }

        private fun findByQualifier(
            languageCode: String,
        ): SupportedAppLanguage? {
            return entries
                .filter { it.isTranslatable }
                .firstOrNull { it.matchesQualifier(languageCode) }
        }

        private const val DEFAULT_VALUES_DIRECTORY_NAME = "values"
        private const val BCP47_PREFIX = "b+"
    }
}

sealed interface ResourceDirectoryLanguage {
    data object Default : ResourceDirectoryLanguage
    data class Known(val language: SupportedAppLanguage) : ResourceDirectoryLanguage
    data object Unsupported : ResourceDirectoryLanguage
}
