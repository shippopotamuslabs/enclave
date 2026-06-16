package com.browntowndev.enclave.feature.chat.delegates

import com.browntowndev.enclave.core.ui.error.ViewModelErrorHandler
import com.browntowndev.enclave.domain.model.chat.ChatModelOverride
import com.browntowndev.enclave.domain.usecase.chat.ChatUseCases
import com.browntowndev.enclave.domain.usecase.chat.StageImageAttachmentUseCase
import com.browntowndev.enclave.feature.chat.ChatModeUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

class ChatInputCoordinator
    @Inject
    constructor(
        private val stageImageAttachmentUseCase: StageImageAttachmentUseCase,
        private val chatUseCases: ChatUseCases,
        private val errorHandler: ViewModelErrorHandler,
    ) {
        private val _inputText = MutableStateFlow("")
        val inputText: StateFlow<String> = _inputText.asStateFlow()

        private val _selectedImageUris = MutableStateFlow<List<String>>(emptyList())
        val selectedImageUris: StateFlow<List<String>> = _selectedImageUris.asStateFlow()

        private val _selectedFileName = MutableStateFlow<String?>(null)
        val selectedFileName: StateFlow<String?> = _selectedFileName.asStateFlow()

        private val _selectedFileText = MutableStateFlow<String?>(null)
        val selectedFileText: StateFlow<String?> = _selectedFileText.asStateFlow()

        private val _selectedMode = MutableStateFlow(ChatModeUi.FAST)
        val selectedMode: StateFlow<ChatModeUi> = _selectedMode.asStateFlow()

        private val _selectedModelOverride = MutableStateFlow<ChatModelOverride?>(null)
        val selectedModelOverride: StateFlow<ChatModelOverride?> = _selectedModelOverride.asStateFlow()

        fun onInputChange(text: String) {
            _inputText.value = text
        }

        fun onModeChange(mode: ChatModeUi) {
            if (mode != ChatModeUi.OTHER) {
                clearModelOverride()
            }
            _selectedMode.value = mode
        }

        fun onModelOverrideSelected(override: ChatModelOverride) {
            _selectedModelOverride.value = override
            _selectedMode.value = ChatModeUi.OTHER
        }

        fun clearModelOverride() {
            _selectedModelOverride.value = null
        }

        fun onImagesSelected(
            uris: List<String>,
            scope: CoroutineScope,
            maxItems: Int,
            append: Boolean = true,
        ) {
            if (uris.isEmpty()) return

            scope.launch(
                errorHandler.coroutineExceptionHandler(
                    TAG,
                    "Failed to prepare images",
                    "Could not attach the selected images. Please try again.",
                ),
            ) {
                val currentStagedUris = if (append) _selectedImageUris.value else emptyList()
                val availableSlots = maxItems - currentStagedUris.size
                if (availableSlots <= 0) return@launch
                val limitedNewUris = uris.take(availableSlots)
                val newlyStagedUris = limitedNewUris.map { stageImageAttachmentUseCase(it) }
                _selectedImageUris.value = currentStagedUris + newlyStagedUris
            }
        }

        fun removeImage(uri: String) {
            _selectedImageUris.update { current -> current.filter { it != uri } }
        }

        fun clearSelectedImages() {
            _selectedImageUris.value = emptyList()
        }

        fun trimSelectedImages(maxItems: Int) {
            _selectedImageUris.update { uris -> uris.take(maxItems) }
        }

        fun onFileSelected(
            uri: String?,
            scope: CoroutineScope,
        ) {
            if (uri == null) {
                _selectedFileName.value = null
                _selectedFileText.value = null
                return
            }
            scope.launch(
                errorHandler.coroutineExceptionHandler(
                    TAG,
                    "Failed to process file",
                    "Could not read the selected file. Please try again.",
                ),
            ) {
                val metadata = chatUseCases.processFileAttachmentUseCase(uri)
                _selectedFileName.value = metadata.name
                _selectedFileText.value = metadata.content
            }
        }

        fun clearSelectedFile() {
            _selectedFileName.value = null
            _selectedFileText.value = null
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
