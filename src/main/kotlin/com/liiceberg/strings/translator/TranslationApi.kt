package com.liiceberg.strings.translator

import com.liiceberg.strings.translator.model.TranslateRequest
import com.liiceberg.strings.translator.model.TranslateResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface TranslationApi {

    @POST("/translate")
    suspend fun translate(
        @Body request: TranslateRequest
    ): TranslateResponse

}