package com.liiceberg.strings.translator

import com.github.pemistahl.lingua.api.Language
import java.util.Locale

enum class SupportedAppLanguage(
    val displayName: String,
    val mbartCode: String,
    val androidQualifier: String,
    val linguaLanguage: Language? = null,
) {
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
    GALICIAN("Galician", "gl_ES", "gl"),
    GEORGIAN("Georgian", "ka_GE", "ka", Language.GEORGIAN),
    GERMAN("German", "de_DE", "de", Language.GERMAN),
    GUJARATI("Gujarati", "gu_IN", "gu", Language.GUJARATI),
    HEBREW("Hebrew", "he_IL", "he", Language.HEBREW),
    HINDI("Hindi", "hi_IN", "hi", Language.HINDI),
    INDONESIAN("Indonesian", "id_ID", "id", Language.INDONESIAN),
    ITALIAN("Italian", "it_IT", "it", Language.ITALIAN),
    JAPANESE("Japanese", "ja_XX", "ja", Language.JAPANESE),
    KAZAKH("Kazakh", "kk_KZ", "kk", Language.KAZAKH),
    KHMER("Khmer", "km_KH", "km"),
    KOREAN("Korean", "ko_KR", "ko", Language.KOREAN),
    LATVIAN("Latvian", "lv_LV", "lv", Language.LATVIAN),
    LITHUANIAN("Lithuanian", "lt_LT", "lt", Language.LITHUANIAN),
    MACEDONIAN("Macedonian", "mk_MK", "mk", Language.MACEDONIAN),
    MALAYALAM("Malayalam", "ml_IN", "ml"),
    MARATHI("Marathi", "mr_IN", "mr", Language.MARATHI),
    MYANMAR("Myanmar (Burmese)", "my_MM", "my"),
    NEPALI("Nepali", "ne_NP", "ne"),
    PERSIAN("Persian", "fa_IR", "fa", Language.PERSIAN),
    POLISH("Polish", "pl_PL", "pl", Language.POLISH),
    PORTUGUESE("Portuguese", "pt_XX", "pt", Language.PORTUGUESE),
    PASHTO("Pashto", "ps_AF", "ps"),
    ROMANIAN("Romanian", "ro_RO", "ro", Language.ROMANIAN),
    RUSSIAN("Russian", "ru_RU", "ru", Language.RUSSIAN),
    SINHALA("Sinhala", "si_LK", "si"),
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

    val androidValuesDirectoryName: String = "values-$androidQualifier"

    override fun toString(): String = displayName

    fun matchesQualifier(languageCode: String, script: String?, region: String?): Boolean {
        val normalizedLanguage = languageCode.lowercase(Locale.ROOT)
        val normalizedScript = script?.lowercase(Locale.ROOT)
        val normalizedRegion = region?.uppercase(Locale.ROOT)

        return when (this) {
            CHINESE_SIMPLIFIED -> {
                normalizedLanguage == "zh" &&
                    normalizedScript != "hant" &&
                    normalizedRegion !in setOf("TW", "HK", "MO")
            }
            else -> normalizedLanguage == androidQualifier.substringBefore('-')
        }
    }

    companion object {
        private val byLingua = entries
            .mapNotNull { language -> language.linguaLanguage?.let { it to language } }
            .toMap()

        val dropdownValues: List<SupportedAppLanguage> = entries.sortedBy { it.displayName }

        fun detectableLanguages(): Array<Language> = entries
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
                val script = parts.getOrNull(2)?.takeIf { it.length == 4 }
                val region = parts
                    .drop(if (script == null) 2 else 3)
                    .firstOrNull()
                    ?.takeIf { it.length == 2 || it.length == 3 }
                return findByQualifier(languageCode, script, region)
            }

            val segments = qualifier.split('-')
            val languageCode = segments.firstOrNull()?.takeIf { it.length == 2 } ?: return null
            val region = segments
                .drop(1)
                .firstOrNull { it.startsWith(REGION_PREFIX) && it.length == 3 }
                ?.removePrefix(REGION_PREFIX)
            return findByQualifier(languageCode, null, region)
        }

        private fun findByQualifier(
            languageCode: String,
            script: String?,
            region: String?,
        ): SupportedAppLanguage? {
            return entries.firstOrNull { it.matchesQualifier(languageCode, script, region) }
        }

        private const val DEFAULT_VALUES_DIRECTORY_NAME = "values"
        private const val BCP47_PREFIX = "b+"
        private const val REGION_PREFIX = "r"
    }
}

sealed interface ResourceDirectoryLanguage {
    data object Default : ResourceDirectoryLanguage
    data class Known(val language: SupportedAppLanguage) : ResourceDirectoryLanguage
    data object Unsupported : ResourceDirectoryLanguage
}
