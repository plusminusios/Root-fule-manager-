package com.example

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ── Data models ──────────────────────────────────────────────

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
    val candidates: List<Candidate>? = null,
    val error: GeminiError? = null
)

@Serializable
data class GeminiError(
    val code: Int? = null,
    val message: String? = null,
    val status: String? = null
)

@Serializable
data class Candidate(
    val content: Content? = null
)

// ── Retrofit service ─────────────────────────────────────────
// FIX: was "gemini-3.1-pro-preview" — that model does not exist.
// Using gemini-1.5-flash: fast, free tier, widely available.

interface GeminiApiService {
    @POST("v1beta/models/gemini-1.5-flash:generateContent")
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
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(GeminiApiService::class.java)
    }
}

// ── Shared error handler ─────────────────────────────────────

private fun handleApiError(e: Exception): String {
    return when (e) {
        is retrofit2.HttpException -> when (e.code()) {
            400 -> "Ошибка 400: Неверный запрос. Проверьте формат данных."
            401 -> "Ошибка 401: Неверный API ключ. Обновите ключ в разделе ИИ."
            403 -> "Ошибка 403: Доступ запрещён. Проверьте API ключ и квоты."
            429 -> "Ошибка 429: Превышен лимит запросов. Подождите немного и попробуйте снова."
            500, 503 -> "Ошибка сервера Gemini (${e.code()}). Попробуйте позже."
            else -> "HTTP ошибка ${e.code()}: ${e.message()}"
        }
        is java.net.UnknownHostException -> "Нет подключения к интернету."
        is java.net.SocketTimeoutException -> "Превышено время ожидания. Проверьте соединение."
        else -> "Ошибка: ${e.localizedMessage ?: e.message}"
    }
}

// ── Log analyzer ─────────────────────────────────────────────
// FIX: previously computed finalKey but passed apiKey to the API call — bug.

suspend fun analyzeErrorLog(logText: String, apiKey: String): String = withContext(Dispatchers.IO) {
    if (apiKey.isBlank()) {
        return@withContext "⚠️ Пожалуйста, введите API ключ Gemini в разделе ИИ."
    }

    val request = GenerateContentRequest(
        contents = listOf(
            Content(
                parts = listOf(
                    Part(
                        text = "Проанализируй следующий лог ошибок. " +
                               "Объясни причину и предложи конкретные шаги по исправлению:\n\n$logText"
                    )
                )
            )
        ),
        systemInstruction = Content(
            parts = listOf(
                Part(
                    text = "Ты эксперт по Android и Linux системам. Анализируй логи, " +
                           "чётко указывай причину ошибки и давай пошаговые инструкции по исправлению. " +
                           "Отвечай на русском языке. Используй эмодзи для структуры."
                )
            )
        )
    )

    try {
        // FIX: was passing `apiKey` here even after computing `finalKey` — now uses the passed key directly
        val response = RetrofitClient.service.generateContent(apiKey, request)
        response.error?.message?.let { return@withContext "❌ Ошибка API: $it" }
        response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: "Не удалось получить ответ. Попробуйте ещё раз."
    } catch (e: Exception) {
        handleApiError(e)
    }
}

// ── Free chat with AI ─────────────────────────────────────────
// NEW: general-purpose Gemini chat for Android/Linux questions

suspend fun chatWithGemini(message: String, apiKey: String): String = withContext(Dispatchers.IO) {
    if (apiKey.isBlank()) {
        return@withContext "⚠️ Пожалуйста, введите API ключ Gemini в разделе ИИ."
    }

    val request = GenerateContentRequest(
        contents = listOf(
            Content(parts = listOf(Part(text = message)))
        ),
        systemInstruction = Content(
            parts = listOf(
                Part(
                    text = "Ты ассистент-эксперт по Android, Linux и root-системам. " +
                           "Помогаешь пользователям разбираться в командах терминала, " +
                           "системных настройках и устранении неполадок. " +
                           "Отвечай кратко, ясно и по делу на русском языке. " +
                           "Используй эмодзи для структурирования ответа."
                )
            )
        )
    )

    try {
        val response = RetrofitClient.service.generateContent(apiKey, request)
        response.error?.message?.let { return@withContext "❌ Ошибка API: $it" }
        response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: "Не удалось получить ответ. Попробуйте ещё раз."
    } catch (e: Exception) {
        handleApiError(e)
    }
}
