package com.liiceberg.strings.translator

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object TranslationApiClient {

    private const val BASE_URL = "http://localhost:8000/"

    val api: TranslationApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TranslationApi::class.java)
    }
}