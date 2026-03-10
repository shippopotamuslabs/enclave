package com.browntowndev.pocketcrew.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.browntowndev.pocketcrew.domain.model.download.DownloadModelsResult
import com.browntowndev.pocketcrew.domain.model.download.ModelScanResult
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.usecase.download.InitializeModelsUseCase
import com.browntowndev.pocketcrew.presentation.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AppStartupState {
    data object Loading : AppStartupState
    data class Ready(val modelsResult: DownloadModelsResult?, val errorMessage: String? = null) : AppStartupState {
        val initialRoute = if (errorMessage != null || modelsResult?.modelsToDownload?.isNotEmpty() == true) Routes.MODEL_DOWNLOAD else Routes.CHAT
    }
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val initializeModelsUseCase: InitializeModelsUseCase,
    private val logPort: LoggingPort
) : ViewModel() {

    private val _startupState = MutableStateFlow<AppStartupState>(AppStartupState.Loading)
    val startupState: StateFlow<AppStartupState> = _startupState.asStateFlow()

    init {
        initializeApp()
    }

    private fun initializeApp() {
        viewModelScope.launch {
            try {
                // Suspends until network fetch, DB update, and cache init complete
                // Returns DownloadModelsResult with scan result to avoid duplicate scanning
                val modelsResult = initializeModelsUseCase()
                logPort.debug("MainViewModel", "Models result: $modelsResult")

                _startupState.update { AppStartupState.Ready(modelsResult) }
            } catch (e: Exception) {
                logPort.error("MainViewModel", "Critical failure during app initialization: ${e.message}", e)
                // Fallback: direct to download screen with error to allow recovery/re-sync
                // Return an empty modelsResult so DownloadViewModel can still function
                _startupState.update {
                    AppStartupState.Ready(
                        modelsResult = DownloadModelsResult(
                            modelsToDownload = emptyList(),
                            scanResult = ModelScanResult(
                                missingModels = emptyList(),
                                partialDownloads = emptyMap(),
                                allValid = false
                            )
                        ),
                        errorMessage = "Failed to initialize models"
                    )
                }
            }
        }
    }
}