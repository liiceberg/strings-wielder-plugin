package com.liiceberg.strings.semantic

import com.liiceberg.strings.semantic.model.SemanticDuplicateRequest
import com.liiceberg.strings.semantic.model.SemanticDuplicateResponse

class SemanticDuplicateSearcher {

    private val cache = mutableMapOf<SemanticComparisonKey, Boolean>()

    fun isSemanticDuplicate(
        text: String,
        compareWith: String,
    ): Boolean {
        if (text.isBlank() || compareWith.isBlank()) return false
        if (text == compareWith) return true

        val cacheKey = SemanticComparisonKey.of(text, compareWith)
        cache[cacheKey]?.let { return it }

        val result = runCatching {
            SemanticDuplicateApiClient.api.compare(
                SemanticDuplicateRequest(
                    text = text,
                    compareWith = compareWith,
                )
            ).execute()
        }.getOrNull()

        val isDuplicate = when {
            result == null || !result.isSuccessful -> false
            else -> parseResponse(result.body())
        }

        cache[cacheKey] = isDuplicate
        return isDuplicate
    }

    private fun parseResponse(body: SemanticDuplicateResponse?): Boolean {
        if (body == null) return false
        return body.isDuplicate == true
    }

    private data class SemanticComparisonKey(
        val left: String,
        val right: String,
    ) {
        companion object {
            fun of(left: String, right: String): SemanticComparisonKey {
                val normalizedLeft = left.trim()
                val normalizedRight = right.trim()
                return if (normalizedLeft <= normalizedRight) {
                    SemanticComparisonKey(normalizedLeft, normalizedRight)
                } else {
                    SemanticComparisonKey(normalizedRight, normalizedLeft)
                }
            }
        }
    }

}
