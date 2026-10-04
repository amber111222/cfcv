package com.example.arsen

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SolverViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SolverRepository()
    val preferences = AppPreferences(application)

    private val _uiState = MutableStateFlow<UiState>(UiState.Initial)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _currentBitmap = MutableStateFlow<Bitmap?>(null)
    val currentBitmap: StateFlow<Bitmap?> = _currentBitmap.asStateFlow()

    private val _answerMode = MutableStateFlow(
        try {
            AnswerMode.valueOf(preferences.answerMode)
        } catch (_: Exception) {
            AnswerMode.SHORT
        }
    )
    val answerMode: StateFlow<AnswerMode> = _answerMode.asStateFlow()

    private val _customInstruction = MutableStateFlow("")
    val customInstruction: StateFlow<String> = _customInstruction.asStateFlow()

    fun onImageSelected(bitmap: Bitmap) {
        _currentBitmap.value = bitmap
        _uiState.value = UiState.Initial
    }

    fun clearImage() {
        _currentBitmap.value = null
        _uiState.value = UiState.Initial
    }

    fun setMode(mode: AnswerMode) {
        _answerMode.value = mode
        preferences.answerMode = mode.name
    }

    fun setCustomInstruction(text: String) {
        _customInstruction.value = text
    }

    fun saveSettings(apiKey: String, modelName: String) {
        preferences.apiKey = apiKey
        preferences.modelName = modelName
    }

    fun solve() {
        val bitmap = _currentBitmap.value ?: return

        _uiState.value = UiState.Loading

        viewModelScope.launch {
            val result = repository.solveTest(
                bitmap = bitmap,
                mode = _answerMode.value,
                customInstruction = _customInstruction.value,
                apiKey = preferences.apiKey,
                modelName = preferences.modelName
            )

            result.onSuccess { output ->
                _uiState.value = UiState.Success(output)
            }.onFailure { exception ->
                _uiState.value = UiState.Error(
                    exception.localizedMessage ?: "Произошла непредвиденная ошибка при решении."
                )
            }
        }
    }
}
