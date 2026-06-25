package com.fintrackai.data.remote

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

data class ChatMessage(val role: String, val content: String)

data class GroqRequest(
    val model: String = "llama-3.3-70b-versatile",
    val messages: List<ChatMessage>,
    val temperature: Double = 0.0
)

data class GroqChoice(val message: ChatMessage)
data class GroqResponse(val choices: List<GroqChoice>)

interface GroqApiService {
    @POST("chat/completions")
    suspend fun chat(
        @Header("Authorization") auth: String,
        @Body request: GroqRequest
    ): GroqResponse
}
