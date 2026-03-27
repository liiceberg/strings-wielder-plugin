package com.liiceberg.strings.translator.model

import com.google.gson.annotations.SerializedName

data class TranslateRequest(
    val text: String,
    @SerializedName("source_lang")
    val sourceLang: String,
    @SerializedName("target_lang")
    val targetLang: String
)