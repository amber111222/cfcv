package com.example.arsen

import android.content.Context
import android.content.SharedPreferences

class AppPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("test_solver_prefs", Context.MODE_PRIVATE)

    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_API_KEY, value.trim()).apply()

    var modelName: String
        get() = prefs.getString(KEY_MODEL_NAME, "gemini-2.5-flash") ?: "gemini-2.5-flash"
        set(value) = prefs.edit().putString(KEY_MODEL_NAME, value.trim()).apply()

    var answerMode: String
        get() = prefs.getString(KEY_ANSWER_MODE, "SHORT") ?: "SHORT"
        set(value) = prefs.edit().putString(KEY_ANSWER_MODE, value).apply()

    companion object {
        private const val KEY_API_KEY = "key_gemini_api"
        private const val KEY_MODEL_NAME = "key_model_name"
        private const val KEY_ANSWER_MODE = "key_answer_mode"
    }
}
