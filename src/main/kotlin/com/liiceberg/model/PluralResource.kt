package com.liiceberg.model

interface Resource

data class PluralResource(
    val one: String? = null,
    val few: String? = null,
    val many: String? = null,
    val other: String? = null,
    val currentNumber: Int,
) : Resource

data class StringResource(
    val value: String,
) : Resource