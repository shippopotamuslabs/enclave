package com.browntowndev.enclave.feature.studio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.browntowndev.enclave.domain.usecase.media.SaveMediaToGalleryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FullscreenMediaSaveViewModel
    @Inject
    constructor(
        private val saveMediaToGalleryUseCase: SaveMediaToGalleryUseCase,
    ) : ViewModel() {
        private val _saveFeedback = MutableSharedFlow<String>(extraBufferCapacity = 1)
        val saveFeedback: SharedFlow<String> = _saveFeedback.asSharedFlow()

        fun saveToGallery(asset: StudioMediaUi) {
            viewModelScope.launch {
                saveMediaToGalleryUseCase(asset.effectiveLocalUri(), asset.mediaType)
                    .onSuccess { _saveFeedback.emit(SAVE_SUCCESS_MESSAGE) }
                    .onFailure { _saveFeedback.emit(SAVE_FAILURE_MESSAGE) }
            }
        }

        internal companion object {
            const val SAVE_SUCCESS_MESSAGE = "Saved to your device"
            const val SAVE_FAILURE_MESSAGE = "Could not save media"
        }
    }
