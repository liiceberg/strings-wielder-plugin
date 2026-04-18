package com.liiceberg.strings.semantic.model

import com.google.gson.annotations.SerializedName

data class SemanticDuplicateBatchRequest(
    @SerializedName("text")
    val text: String,
    @SerializedName("compare_with")
    val compareWith: List<String>,
)
