package com.fintrackai.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

data class ForexResponse(val rates: Map<String, Double>)

interface ForexApiService {
    @GET("latest")
    suspend fun getRates(
        @Query("from") from: String,
        @Query("to") to: String = "INR"
    ): ForexResponse
}
