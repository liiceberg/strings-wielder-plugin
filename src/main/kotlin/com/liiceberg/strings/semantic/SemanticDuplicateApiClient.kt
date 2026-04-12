package com.liiceberg.strings.semantic

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object SemanticDuplicateApiClient {

    private const val BASE_URL = "http://localhost:8001/"

    val api: SemanticDuplicateApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SemanticDuplicateApi::class.java)
    }
}
