package com.liiceberg.strings.semantic

import com.liiceberg.strings.semantic.model.SemanticDuplicateRequest
import com.liiceberg.strings.semantic.model.SemanticDuplicateBatchRequest
import com.liiceberg.strings.semantic.model.SemanticDuplicateBatchResponse
import com.liiceberg.strings.semantic.model.SemanticDuplicateResponse
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface SemanticDuplicateApi {

    @POST("analyze-batch")
    fun compareBatch(
        @Body request: SemanticDuplicateBatchRequest,
    ): Call<SemanticDuplicateBatchResponse>

    @POST("analyze")
    fun compare(
        @Body request: SemanticDuplicateRequest,
    ): Call<SemanticDuplicateResponse>
}
