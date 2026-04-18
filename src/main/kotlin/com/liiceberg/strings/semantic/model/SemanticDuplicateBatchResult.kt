package com.liiceberg.strings.semantic.model

import com.google.gson.annotations.SerializedName

data class SemanticDuplicateBatchResult(
    @SerializedName("compare_with")
    val compareWith: String,
    @SerializedName("similarity")
    val similarity: Double? = null,
    @SerializedName("is_duplicate")
    val isDuplicate: Boolean? = null,
)
