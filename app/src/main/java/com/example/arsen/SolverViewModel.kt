package com.example.arsen

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SolverViewModel(application: Application) : AndroidViewModel(application) {

    val preferences = AppPreferences(application)
    private val apiService = DeepSeekApiService()

    private val _currentBitmap = MutableStateFlow<Bitmap?>(null)
    val currentBitmap: StateFlow<Bitmap?> = _currentBitmap.asStateFlow()

    private val _currentPhotoUri = MutableStateFlow<Uri?>(null)
    val currentPhotoUri: StateFlow<Uri?> = _currentPhotoUri.asStateFlow()

    private val _selectedSection = MutableStateFlow(
        try {
            AnswerSection.valueOf(preferences.answerSection)
        } catch (_: Exception) {
            AnswerSection.SHORT_ANSWERS
        }
    )
    val selectedSection: StateFlow<AnswerSection> = _selectedSection.asStateFlow()

    private val _currentPrompt = MutableStateFlow(_selectedSection.value.defaultPrompt)
    val currentPrompt: StateFlow<String> = _currentPrompt.asStateFlow()

    private val _testQuestionsText = MutableStateFlow("")
    val testQuestionsText: StateFlow<String> = _testQuestionsText.asStateFlow()

    private val _lastSentPrompt = MutableStateFlow("")
    val lastSentPrompt: StateFlow<String> = _lastSentPrompt.asStateFlow()

    private val _uiState = MutableStateFlow<UiState>(UiState.Initial)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _statusMessage = MutableStateFlow("")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _showEmbeddedWeb = MutableStateFlow(false)
    val showEmbeddedWeb: StateFlow<Boolean> = _showEmbeddedWeb.asStateFlow()

    private val _isDeepSeekInstalled = MutableStateFlow(false)
    val isDeepSeekInstalled: StateFlow<Boolean> = _isDeepSeekInstalled.asStateFlow()

    init {
        checkDeepSeekStatus(application)
    }

    fun checkDeepSeekStatus(context: Context) {
        _isDeepSeekInstalled.value = DeepSeekBridge.isDeepSeekInstalled(context)
    }

    fun onPhotoSelected(bitmap: Bitmap, uri: Uri?) {
        _currentBitmap.value = bitmap
        _currentPhotoUri.value = uri
        if (_uiState.value !is UiState.Success) {
            _uiState.value = UiState.Initial
        }
    }

    fun clearPhoto() {
        _currentBitmap.value = null
        _currentPhotoUri.value = null
        _testQuestionsText.value = ""
        _uiState.value = UiState.Initial
    }

    fun selectSection(section: AnswerSection) {
        _selectedSection.value = section
        preferences.answerSection = section.name
        _currentPrompt.value = section.defaultPrompt
    }

    fun updatePrompt(text: String) {
        _currentPrompt.value = text
    }

    fun updateQuestionsText(text: String) {
        _testQuestionsText.value = text
    }

    fun toggleEmbeddedWeb() {
        _showEmbeddedWeb.value = !_showEmbeddedWeb.value
    }

    fun setStatusMessage(msg: String) {
        _statusMessage.value = msg
    }

    fun saveSettings(apiKey: String, model: String) {
        preferences.deepseekApiKey = apiKey
        preferences.deepseekModel = model
    }

    fun buildFullQuery(): String {
        return buildString {
            append(_currentPrompt.value.trim())
            if (_testQuestionsText.value.isNotBlank()) {
                append("\n\nВОПРОСЫ / ТЕКСТ ТЕСТА:\n")
                append(_testQuestionsText.value.trim())
            }
        }
    }

    /**
     * Фоновое решение без необходимости внешних API:
     * 1. Если указан ключ DeepSeek API — отправляет HTTP запрос
     * 2. Иначе — отправляет через фоновый встроенный DeepSeek Web Solver
     */
    fun solve(context: Context, webSolver: DeepSeekWebSolver) {
        val apiKey = preferences.deepseekApiKey
        val fullQuery = buildFullQuery()

        _uiState.value = UiState.Loading

        if (apiKey.isNotBlank()) {
            viewModelScope.launch {
                val result = apiService.queryDeepSeek(
                    prompt = fullQuery,
                    apiKey = apiKey,
                    model = preferences.deepseekModel
                )
                result.onSuccess { answer ->
                    _uiState.value = UiState.Success(answer)
                }.onFailure { error ->
                    _uiState.value = UiState.Error(
                        error.localizedMessage ?: "Ошибка при получении ответа от DeepSeek."
                    )
                }
            }
        } else {
            // Без API: отправляем через фоновый веб-мост DeepSeek
            _lastSentPrompt.value = fullQuery
            webSolver.sendPrompt(fullQuery)
        }
    }

    fun onWebAnswerReceived(answer: String) {
        _uiState.value = UiState.Success(answer)
    }

    fun checkClipboardForAnswer(context: Context): Boolean {
        val clipboardText = DeepSeekBridge.getClipboardContent(context, _lastSentPrompt.value)
        if (!clipboardText.isNullOrBlank()) {
            _uiState.value = UiState.Success(clipboardText)
            return true
        }
        return false
    }

    fun clearAnswer() {
        _uiState.value = UiState.Initial
    }
}
