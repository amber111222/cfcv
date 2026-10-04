package com.example.arsen

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SolverViewModel(application: Application) : AndroidViewModel(application) {

    private val _currentBitmap = MutableStateFlow<Bitmap?>(null)
    val currentBitmap: StateFlow<Bitmap?> = _currentBitmap.asStateFlow()

    private val _currentPhotoUri = MutableStateFlow<Uri?>(null)
    val currentPhotoUri: StateFlow<Uri?> = _currentPhotoUri.asStateFlow()

    private val _selectedSection = MutableStateFlow(AnswerSection.SHORT_ANSWERS)
    val selectedSection: StateFlow<AnswerSection> = _selectedSection.asStateFlow()

    private val _currentPrompt = MutableStateFlow(AnswerSection.SHORT_ANSWERS.defaultPrompt)
    val currentPrompt: StateFlow<String> = _currentPrompt.asStateFlow()

    private val _lastSentPrompt = MutableStateFlow("")
    val lastSentPrompt: StateFlow<String> = _lastSentPrompt.asStateFlow()

    private val _uiState = MutableStateFlow<UiState>(UiState.Initial)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

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
        _uiState.value = UiState.Initial
    }

    fun selectSection(section: AnswerSection) {
        _selectedSection.value = section
        _currentPrompt.value = section.defaultPrompt
    }

    fun updatePrompt(text: String) {
        _currentPrompt.value = text
    }

    fun sendToDeepSeek(context: Context) {
        val prompt = _currentPrompt.value.trim()
        val uri = _currentPhotoUri.value

        DeepSeekBridge.sendRequestToDeepSeek(
            context = context,
            photoUri = uri,
            prompt = prompt,
            onPromptCopied = { copied ->
                _lastSentPrompt.value = copied
            }
        )

        _uiState.value = UiState.Loading
    }

    /**
     * Проверяет буфер обмена: если обнаружен ответ из DeepSeek, импортирует его в приложение
     */
    fun checkClipboardForAnswer(context: Context): Boolean {
        val clipboardText = DeepSeekBridge.getClipboardContent(context, _lastSentPrompt.value)
        if (!clipboardText.isNullOrBlank()) {
            _uiState.value = UiState.Success(clipboardText)
            return true
        }
        return false
    }

    fun setAnswer(text: String) {
        _uiState.value = UiState.Success(text)
    }

    fun clearAnswer() {
        _uiState.value = UiState.Initial
    }
}
