package com.example

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Serializable
data class GenerateContentRequest(
    val contents: List<Content>,
    val systemInstruction: Content? = null
)

@Serializable
data class Content(
    val parts: List<Part>
)

@Serializable
data class Part(
    val text: String? = null
)

@Serializable
data class GenerateContentResponse(
    val candidates: List<Candidate>? = null
)

@Serializable
data class Candidate(
    val content: Content? = null
)

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.1-pro-preview:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

object RetrofitClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    val service: GeminiApiService by lazy {
        val json = Json { ignoreUnknownKeys = true }
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        retrofit.create(GeminiApiService::class.java)
    }
}

suspend fun analyzeErrorLog(logText: String, apiKey: String): String = withContext(Dispatchers.IO) {
    val finalKey = if (apiKey == "AIStudio_Google_Logged_In_Token") {
        // Here we could use a restricted key or server-side capability
        // For now, let's assume we use the one from secrets or same key logic
        apiKey 
    } else {
        apiKey
    }
    
    if (finalKey.isEmpty() || finalKey == "YOUR_API_KEY_HERE") {
        return@withContext "Ошибка: Пожалуйста, войдите, чтобы использовать функции анализа."
    }
    
    val request = GenerateContentRequest(
        contents = listOf(Content(
            parts = listOf(Part(text = "Пожалуйста, проанализируй следующий лог ошибок или системный лог. Объясни, почему возникла эта ошибка, и предложи конкретные шаги по её исправлению на русском языке:\n\n$logText"))
        )),
        systemInstruction = Content(
            parts = listOf(Part(text = "Ты эксперт по Android и Linux системам. Твоя задача — анализировать логи, находить причину ошибки и давать чёткие, понятные инструкции по исправлению."))
        )
    )
    
    try {
        val response = RetrofitClient.service.generateContent(apiKey, request)
        response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: "Не удалось получить ответ от сервера."
    } catch (e: Exception) {
        "Произошла ошибка при запросе: ${e.message}"
    }
}
