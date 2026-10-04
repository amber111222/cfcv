package com.example.arsen

import android.graphics.Bitmap
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class AnswerMode(val title: String, val shortTitle: String, val description: String) {
    SHORT("⚡ Только ответы", "Кратко", "Готовые ответы для быстрого списывания или проверки"),
    STANDARD("💡 Ответ с пояснением", "С пояснением", "Точный ответ + краткое объяснение почему"),
    DETAILED("📚 Полное решение", "Подробно", "Пошаговый ход решения с формулами")
}

class SolverRepository {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(45, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .build()

    suspend fun solveTest(
        bitmap: Bitmap,
        mode: AnswerMode,
        customInstruction: String,
        apiKey: String,
        modelName: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val prompt = buildPrompt(mode, customInstruction)

        // 1. Если указан Gemini API ключ — используем прямой Gemini REST API
        if (apiKey.isNotBlank()) {
            val result = callGeminiRestApi(bitmap, prompt, apiKey, modelName)
            if (result.isSuccess) {
                return@withContext result
            }
            // Если была ошибка с прямым API, возвращаем её
            return@withContext result
        }

        // 2. Если API ключ не указан — пробуем Firebase AI
        try {
            val generativeModel = Firebase.ai.generativeModel(
                modelName = if (modelName.contains("flash")) modelName else "gemini-flash-latest"
            )
            val response = generativeModel.generateContent(
                content {
                    image(bitmap)
                    text(prompt)
                }
            )
            val text = response.text
            if (!text.isNullOrBlank()) {
                return@withContext Result.success(text)
            }
        } catch (e: Exception) {
            // Если и Firebase AI не сработал — даем подробную инструкцию
            val errorMsg = e.localizedMessage ?: e.message ?: "Неизвестная ошибка"
            return@withContext Result.failure(
                Exception(
                    "Не удалось отправить запрос через Firebase: $errorMsg.\n\n" +
                            "👉 Рекомендуется ввести свой бесплатный Gemini API ключ:\n" +
                            "1. Нажмите на значок ⚙️ (Настройки) в правом верхнем углу.\n" +
                            "2. Получите бесплатный ключ за 1 минуту на https://aistudio.google.com/apikey\n" +
                            "3. Вставьте ключ и нажмите «Сохранить»."
                )
            )
        }

        return@withContext Result.failure(
            Exception(
                "Пожалуйста, введите ваш API ключ в настройках ⚙️ (в правом верхнем углу экрана).\n" +
                        "Получить бесплатный ключ Google Gemini можно на https://aistudio.google.com/apikey"
            )
        )
    }

    private fun buildPrompt(mode: AnswerMode, customInstruction: String): String {
        return buildString {
            append("Ты — персональный ассистент по решению тестов, самостоятельных и контрольных работ (КР).\n")
            append("ВНИМАТЕЛЬНО И ТОЧНО РАСПОЗНАЙ РУССКИЙ ТЕКСТ НА ФОТОГРАФИИ (печатный или рукописный, вопросы, варианты ответов А, Б, В, Г или 1, 2, 3, 4, таблицы, формулы).\n\n")

            when (mode) {
                AnswerMode.SHORT -> {
                    append("ФОРМАТ ВЫВОДА (ТОЛЬКО КРАТКИЕ ГОТОВЫЕ ОТВЕТЫ):\n")
                    append("Для каждого задания выведи номер и прямой правильный ответ без лишней воды. Например:\n")
                    append("№1: В\n")
                    append("№2: 42\n")
                    append("№3: А, Г\n")
                    append("№4: Фотосинтез\n\n")
                    append("Если есть варианты выбора, укажи букву и сам вариант.\n")
                }
                AnswerMode.STANDARD -> {
                    append("ФОРМАТ ВЫВОДА:\n")
                    append("Для каждого задания на фото сформируй:\n")
                    append("№ [номер задания]\n")
                    append("Вопрос: [краткий текст вопроса]\n")
                    append("✅ Ответ: [буква и правильный вариант]\n")
                    append("💡 Пояснение: [в 1-2 предложениях, почему именно этот ответ]\n\n")
                }
                AnswerMode.DETAILED -> {
                    append("ФОРМАТ ВЫВОДА:\n")
                    append("Для каждого задания напиши номер, условие, подробное пошаговое решение с формулами и вычислениями, и итоговый ответ.\n\n")
                }
            }

            if (customInstruction.isNotBlank()) {
                append("ДОПОЛНИТЕЛЬНОЕ УКАЗАНИЕ ПОЛЬЗОВАТЕЛЯ:\n")
                append(customInstruction.trim())
                append("\n\n")
            }

            append("Отвечай строго на русском языке, грамотно и точно!")
        }
    }

    private fun callGeminiRestApi(
        bitmap: Bitmap,
        prompt: String,
        apiKey: String,
        modelName: String
    ): Result<String> {
        try {
            val base64Image = ImageUtils.bitmapToBase64(bitmap, quality = 85)

            // Выбираем актуальное имя модели
            val cleanModel = when {
                modelName.startsWith("gemini-") -> modelName
                else -> "gemini-2.5-flash"
            }

            val url = "https://generativelanguage.googleapis.com/v1beta/models/$cleanModel:generateContent?key=$apiKey"

            val jsonBody = JSONObject().apply {
                val contents = JSONArray().apply {
                    val contentObj = JSONObject().apply {
                        val parts = JSONArray().apply {
                            // Текстовый промпт
                            put(JSONObject().apply {
                                put("text", prompt)
                            })
                            // Изображение Base64
                            put(JSONObject().apply {
                                val inlineData = JSONObject().apply {
                                    put("mime_type", "image/jpeg")
                                    put("data", base64Image)
                                }
                                put("inline_data", inlineData)
                            })
                        }
                        put("parts", parts)
                    }
                    put(contentObj)
                }
                put("contents", contents)

                val generationConfig = JSONObject().apply {
                    put("temperature", 0.2)
                    put("maxOutputTokens", 2048)
                }
                put("generationConfig", generationConfig)
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = jsonBody.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                var errorText = "HTTP ${response.code}"
                try {
                    val errJson = JSONObject(responseBody)
                    val errorObj = errJson.optJSONObject("error")
                    if (errorObj != null) {
                        errorText = errorObj.optString("message", errorText)
                    }
                } catch (_: Exception) {}

                return Result.failure(
                    Exception("Ошибка Gemini API ($errorText). Проверьте ключ API и название модели в настройках ⚙️.")
                )
            }

            val jsonResponse = JSONObject(responseBody)
            val candidates = jsonResponse.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                val firstCandidate = candidates.getJSONObject(0)
                val content = firstCandidate.optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                if (parts != null && parts.length() > 0) {
                    val textBuilder = StringBuilder()
                    for (i in 0 until parts.length()) {
                        val part = parts.getJSONObject(i)
                        textBuilder.append(part.optString("text", ""))
                    }
                    val resultText = textBuilder.toString().trim()
                    if (resultText.isNotEmpty()) {
                        return Result.success(resultText)
                    }
                }
            }

            return Result.failure(Exception("Пустой ответ от Gemini. Попробуйте сделать более чёткое фото."))
        } catch (e: Exception) {
            return Result.failure(Exception("Сетевая ошибка: ${e.localizedMessage ?: e.message}"))
        }
    }
}
