package com.liiceberg.strings.translator

import com.liiceberg.model.PluralResource
import com.liiceberg.model.Resource
import com.liiceberg.model.StringResource
import com.liiceberg.strings.translator.model.TranslateRequest
import kotlinx.coroutines.delay
import java.util.concurrent.ConcurrentHashMap

class Translator {
    private val cache = ConcurrentHashMap<TranslationCacheKey, String>()

    suspend fun translate(
        text: String,
        sourceLanguage: SupportedAppLanguage,
        targetLanguage: SupportedAppLanguage,
    ): String {
        if (text.isBlank() || sourceLanguage == targetLanguage) return text

        val cacheKey = TranslationCacheKey(text, sourceLanguage, targetLanguage)
        cache[cacheKey]?.let { return it }

        val translatedText = cache[cacheKey] ?: retryTranslate(text, sourceLanguage, targetLanguage)


        cache[cacheKey] = translatedText
        return translatedText
    }

    suspend fun translate(
        resource: Resource,
        sourceLanguage: SupportedAppLanguage,
        targetLanguage: SupportedAppLanguage,
    ): Resource {
        if (sourceLanguage == targetLanguage) return resource

        return when (resource) {
            is StringResource -> {
                StringResource(
                    value = translate(resource.value, sourceLanguage, targetLanguage)
                )
            }
            is PluralResource -> {
                PluralResource(
                    zero = translateOptional(resource.zero, sourceLanguage, targetLanguage),
                    one = translateOptional(resource.one, sourceLanguage, targetLanguage),
                    few = translateOptional(resource.few, sourceLanguage, targetLanguage),
                    many = translateOptional(resource.many, sourceLanguage, targetLanguage),
                    other = translateOptional(resource.other, sourceLanguage, targetLanguage),
                    currentNumber = resource.currentNumber,
                )
            }
            else -> resource
        }
    }

    private suspend fun translateOptional(
        text: String?,
        sourceLanguage: SupportedAppLanguage,
        targetLanguage: SupportedAppLanguage,
    ): String? {
        val value = text?.takeIf { it.isNotBlank() } ?: return text
        return translate(value, sourceLanguage, targetLanguage)
    }

    private data class TranslationCacheKey(
        val text: String,
        val sourceLanguage: SupportedAppLanguage,
        val targetLanguage: SupportedAppLanguage,
    )

    private suspend fun retryTranslate(
        text: String,
        sourceLanguage: SupportedAppLanguage,
        targetLanguage: SupportedAppLanguage,
    ): String {
        var lastError: Throwable? = null

        repeat(MAX_RETRY_ATTEMPTS) { attempt ->
            try {
                return ApiClient.api.translate(
                    TranslateRequest(
                        text = text,
                        sourceLang = sourceLanguage.mbartCode,
                        targetLang = targetLanguage.mbartCode,
                    )
                ).translation
            } catch (error: Throwable) {
                lastError = error
                if (attempt < MAX_RETRY_ATTEMPTS - 1) {
                    delay(RETRY_DELAY_MS * (attempt + 1))
                }
            }
        }

        throw lastError ?: IllegalStateException("Translation failed without explicit error")
    }

    private companion object {
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 150L
    }
}
