package com.example.arsen

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class DeepSeekApiService {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Отправляет фоновый запрос в DeepSeek API (api.deepseek.com)
     */
    suspend fun queryDeepSeek(
        prompt: String,
        apiKey: String,
        model: String = "deepseek-chat"
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.deepseek.com/chat/completions"

            val jsonBody = JSONObject().apply {
                put("model", model)
                val messages = JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", "Ты — экспертный решатель школьных и университетских тестов и контрольных работ на русском языке. Всегда отвечай строго по заданному формату.")
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    })
                }
                put("messages", messages)
                put("temperature", 0.3)
                put("max_tokens", 2048)
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = jsonBody.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer ${apiKey.trim()}")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                var errorMsg = "HTTP ${response.code}"
                try {
                    val errJson = JSONObject(responseBody)
                    val errorObj = errJson.optJSONObject("error")
                    if (errorObj != null) {
                        errorMsg = errorObj.optString("message", errorMsg)
                    }
                } catch (_: Exception) {}
                return@withContext Result.failure(Exception("Ошибка DeepSeek API: $errorMsg"))
            }

            val jsonResponse = JSONObject(responseBody)
            val choices = jsonResponse.optJSONArray("choices")
            if (choices != null && choices.length() > 0) {
                val firstChoice = choices.getJSONObject(0)
                val message = firstChoice.optJSONObject("message")
                val content = message?.optString("content", "")?.trim() ?: ""
                if (content.isNotEmpty()) {
                    return@withContext Result.success(content)
                }
            }

            return@withContext Result.failure(Exception("Получен пустой ответ от DeepSeek"))
        } catch (e: Exception) {
            return@withContext Result.failure(Exception("Ошибка подключения к DeepSeek: ${e.localizedMessage ?: e.message}"))
        }
    }
}
