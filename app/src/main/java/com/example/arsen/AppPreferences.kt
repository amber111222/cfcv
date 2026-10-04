package com.example.arsen

import android.content.Context
import android.content.SharedPreferences

class AppPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("test_solver_prefs", Context.MODE_PRIVATE)

    var deepseekApiKey: String
        get() = prefs.getString(KEY_DEEPSEEK_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_DEEPSEEK_API_KEY, value.trim()).apply()

    var deepseekModel: String
        get() = prefs.getString(KEY_DEEPSEEK_MODEL, "deepseek-chat") ?: "deepseek-chat"
        set(value) = prefs.edit().putString(KEY_DEEPSEEK_MODEL, value.trim()).apply()

    var answerSection: String
        get() = prefs.getString(KEY_ANSWER_SECTION, "SHORT_ANSWERS") ?: "SHORT_ANSWERS"
        set(value) = prefs.edit().putString(KEY_ANSWER_SECTION, value).apply()

    companion object {
        private const val KEY_DEEPSEEK_API_KEY = "key_deepseek_api"
        private const val KEY_DEEPSEEK_MODEL = "key_deepseek_model"
        private const val KEY_ANSWER_SECTION = "key_answer_section"
    }
}
