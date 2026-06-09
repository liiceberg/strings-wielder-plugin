package com.liiceberg.strings.semantic

import com.liiceberg.strings.semantic.model.SemanticDuplicateBatchRequest
import com.liiceberg.strings.semantic.model.SemanticDuplicateBatchResponse
import com.liiceberg.strings.semantic.model.SemanticDuplicateRequest
import com.liiceberg.strings.semantic.model.SemanticDuplicateResponse
import java.util.concurrent.ConcurrentHashMap

class SemanticDuplicateSearcher {

    private val cache = ConcurrentHashMap<SemanticComparisonKey, Boolean>()
    @Volatile
    private var isBatchEndpointAvailable: Boolean? = null

    private fun isSemanticDuplicate(
        text: String,
        compareWith: String,
    ): Boolean {
        if (text.isBlank() || compareWith.isBlank()) return false
        if (text == compareWith) return true

        val cacheKey = SemanticComparisonKey.of(text, compareWith)
        return cache.computeIfAbsent(cacheKey) {
            requestSingleComparison(text, compareWith)
        }
    }

    fun findSemanticDuplicates(
        text: String,
        compareWith: List<String>,
    ): Set<String> {
        if (text.isBlank()) return emptySet()

        val uniqueCandidates = compareWith
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        if (uniqueCandidates.isEmpty()) return emptySet()

        val matches = mutableSetOf<String>()
        val uncachedCandidates = mutableListOf<String>()

        uniqueCandidates.forEach { candidate ->
            if (candidate == text) {
                matches += candidate
                return@forEach
            }

            val cacheKey = SemanticComparisonKey.of(text, candidate)
            val cached = cache[cacheKey]
            if (cached != null) {
                if (cached) matches += candidate
            } else {
                uncachedCandidates += candidate
            }
        }

        if (uncachedCandidates.isNotEmpty()) {
            val batchResults = requestBatchComparisons(text, uncachedCandidates)
            uncachedCandidates.forEach { candidate ->
                val isDuplicate = batchResults[candidate] ?: isSemanticDuplicate(text, candidate)
                cache[SemanticComparisonKey.of(text, candidate)] = isDuplicate
                if (isDuplicate) {
                    matches += candidate
                }
            }
        }

        return matches
    }

    private fun requestSingleComparison(
        text: String,
        compareWith: String,
    ): Boolean {
        val result = runCatching {
            SemanticDuplicateApiClient.api.compare(
                SemanticDuplicateRequest(
                    text = text,
                    compareWith = compareWith,
                )
            ).execute()
        }.getOrNull()

        return when {
            result == null || !result.isSuccessful -> false
            else -> parseResponse(result.body())
        }
    }

    private fun requestBatchComparisons(
        text: String,
        compareWith: List<String>,
    ): Map<String, Boolean> {
        if (isBatchEndpointAvailable == false) return emptyMap()

        val result = runCatching {
            SemanticDuplicateApiClient.api.compareBatch(
                SemanticDuplicateBatchRequest(
                    text = text,
                    compareWith = compareWith,
                )
            ).execute()
        }.getOrNull()

        return when {
            result == null -> emptyMap()
            !result.isSuccessful -> {
                if (result.code() == 404) {
                    isBatchEndpointAvailable = false
                }
                emptyMap()
            }
            else -> {
                isBatchEndpointAvailable = true
                parseBatchResponse(result.body())
            }
        }
    }

    private fun parseResponse(body: SemanticDuplicateResponse?): Boolean {
        if (body == null) return false
        return body.isDuplicate == true
    }

    private fun parseBatchResponse(body: SemanticDuplicateBatchResponse?): Map<String, Boolean> {
        if (body == null) return emptyMap()
        return body.results.associate { result ->
            result.compareWith.trim() to (result.isDuplicate == true)
        }
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
