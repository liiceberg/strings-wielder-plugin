package com.liiceberg.strings.semantic.model

import com.google.gson.annotations.SerializedName

data class SemanticDuplicateBatchResponse(
    @SerializedName("results")
    val results: List<SemanticDuplicateBatchResult> = emptyList(),
)
