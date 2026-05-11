package com.browntowndev.pocketcrew.feature.chat.delegates

import com.browntowndev.pocketcrew.core.ui.error.ViewModelErrorHandler
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.media.SpeechState
import com.browntowndev.pocketcrew.domain.usecase.chat.ChatUseCases
import com.browntowndev.pocketcrew.domain.usecase.chat.StageImageAttachmentUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

class ChatInputCoordinator
    @Inject
    constructor(
        private val stageImageAttachmentUseCase: StageImageAttachmentUseCase,
        private val chatUseCases: ChatUseCases,
        private val errorHandler: ViewModelErrorHandler,
        private val loggingPort: LoggingPort,
    ) {
        private val _inputText = MutableStateFlow("")
        val inputText: StateFlow<String> = _inputText.asStateFlow()

        private val _selectedImageUris = MutableStateFlow<List<String>>(emptyList())
        val selectedImageUris: StateFlow<List<String>> = _selectedImageUris.asStateFlow()

        private val _selectedFileName = MutableStateFlow<String?>(null)
        val selectedFileName: StateFlow<String?> = _selectedFileName.asStateFlow()

        private val _selectedFileText = MutableStateFlow<String?>(null)
        val selectedFileText: StateFlow<String?> = _selectedFileText.asStateFlow()

        private val _speechState = MutableStateFlow<SpeechState>(SpeechState.Idle)
        val speechState: StateFlow<SpeechState> = _speechState.asStateFlow()

        private val _stopSpeechSignal = MutableStateFlow(false)

        fun onInputChange(text: String) {
            _inputText.value = text
        }

        fun onImagesSelected(
            uris: List<String>,
            scope: CoroutineScope,
            maxItems: Int,
        ) {
            if (uris.isEmpty()) return

            scope.launch(
                errorHandler.coroutineExceptionHandler(
                    TAG,
                    "Failed to prepare images",
                    "Could not attach images. Please try again.",
                ),
            ) {
                val newlyStagedUris = uris.take(maxItems).map { stageImageAttachmentUseCase(it) }
                _selectedImageUris.value = newlyStagedUris
            }
        }

        fun onFileSelected(
            uri: String,
            scope: CoroutineScope,
        ) {
            scope.launch(
                errorHandler.coroutineExceptionHandler(
                    TAG,
                    "Failed to process file",
                    "Could not attach file. Please try again.",
                ),
            ) {
                val metadata = chatUseCases.processFileAttachmentUseCase(uri)
                _selectedFileName.value = metadata.name
                _selectedFileText.value = metadata.content
            }
        }

        fun onMicClick(scope: CoroutineScope) {
            val currentState = _speechState.value
            if (currentState is SpeechState.Listening ||
                currentState is SpeechState.ModelLoading ||
                currentState is SpeechState.Transcribing
            ) {
                _stopSpeechSignal.value = true
                return
            }

            _stopSpeechSignal.value = false
            chatUseCases
                .listenToSpeechUseCase(_inputText.value, _stopSpeechSignal.asStateFlow())
                .onEach { state ->
                    _speechState.value = state
                    if (state is SpeechState.FinalText) {
                        _inputText.value = state.text
                    } else if (state is SpeechState.Error) {
                        loggingPort.error(TAG, "Speech recognition error: ${state.message}")
                        errorHandler.handleError(
                            tag = TAG,
                            message = "Speech Recognition Failed",
                            throwable = Exception(state.message),
                            userMessage = state.message,
                        )
                    }
                }.launchIn(scope)
        }

        fun clearInput() {
            _inputText.value = ""
            _selectedImageUris.value = emptyList()
            _selectedFileName.value = null
            _selectedFileText.value = null
        }

        private companion object {
            private const val TAG = "ChatInputCoordinator"
        }
    }
