package com.liiceberg.strings.semantic.model

import com.google.gson.annotations.SerializedName

data class SemanticDuplicateRequest(
    @SerializedName("text")
    val text: String,
    @SerializedName("compare_with")
    val compareWith: String,
)
