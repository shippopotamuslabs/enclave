package com.browntowndev.pocketcrew.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.browntowndev.pocketcrew.core.data.repository.DownloadWorkRepository
import com.browntowndev.pocketcrew.domain.model.config.LocalModelId
import com.browntowndev.pocketcrew.domain.model.download.DownloadKey
import com.browntowndev.pocketcrew.domain.usecase.modelconfig.ReDownloadModelUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * State representing the re-download progress of a specific local model.
 */
sealed class ReDownloadProgress {
    object Idle : ReDownloadProgress()
    object Preparing : ReDownloadProgress()
    data class Downloading(val progress: Float) : ReDownloadProgress()
    object Complete : ReDownloadProgress()
    data class Failed(val error: String) : ReDownloadProgress()
}

@HiltViewModel
class ModelReDownloadViewModel @Inject constructor(
    private val reDownloadModelUseCase: ReDownloadModelUseCase,
    private val downloadWorkRepository: DownloadWorkRepository
) : ViewModel() {

    private val _reDownloadStates = MutableStateFlow<Map<LocalModelId, ReDownloadProgress>>(emptyMap())
    val reDownloadStates: StateFlow<Map<LocalModelId, ReDownloadProgress>> = _reDownloadStates.asStateFlow()

    fun reDownloadModel(modelId: LocalModelId) {
        viewModelScope.launch {
            _reDownloadStates.value = _reDownloadStates.value + (modelId to ReDownloadProgress.Preparing)

            reDownloadModelUseCase(modelId)
                .onSuccess {
                    observeDownloadProgress(modelId)
                }
                .onFailure { error ->
                    _reDownloadStates.value = _reDownloadStates.value + (modelId to ReDownloadProgress.Failed(error.message ?: "Unknown error"))
                    delay(3000)
                    _reDownloadStates.value = _reDownloadStates.value - modelId
                }
        }
    }

    private fun observeDownloadProgress(modelId: LocalModelId) {
        viewModelScope.launch {
            // Wait a moment for the work to be enqueued
            var workId = downloadWorkRepository.getWorkId()
            var attempts = 0
            while (workId == null && attempts < 5) {
                delay(500)
                workId = downloadWorkRepository.getWorkId()
                attempts++
            }

            if (workId == null) {
                _reDownloadStates.value = _reDownloadStates.value + (modelId to ReDownloadProgress.Failed("Failed to start download"))
                delay(3000)
                _reDownloadStates.value = _reDownloadStates.value - modelId
                return@launch
            }

            downloadWorkRepository.observeDownloadProgress(workId)
                .takeWhile { workInfo ->
                    if (workInfo == null) return@takeWhile false

                    // Extract progress for this model. Since we use ModelType.UNASSIGNED,
                    // we look for that in the files progress.
                    val filesProgress = workInfo.progress.getStringArray(DownloadKey.FILES_PROGRESS.key)
                    val modelProgress = filesProgress?.find { it.contains("unassigned") }

                    if (modelProgress != null) {
                        val parts = modelProgress.split("|")
                        if (parts.size >= 3) {
                            val downloaded = parts[1].toLongOrNull() ?: 0L
                            val total = parts[2].toLongOrNull() ?: 1L
                            val progress = if (total > 0) downloaded.toFloat() / total else 0f
                            _reDownloadStates.value = _reDownloadStates.value + (modelId to ReDownloadProgress.Downloading(progress))
                        }
                    }

                    when (workInfo.state) {
                        WorkInfo.State.SUCCEEDED -> {
                            _reDownloadStates.value = _reDownloadStates.value + (modelId to ReDownloadProgress.Complete)
                            delay(2000)
                            _reDownloadStates.value = _reDownloadStates.value - modelId
                            false
                        }
                        WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                            _reDownloadStates.value = _reDownloadStates.value + (modelId to ReDownloadProgress.Failed("Download failed"))
                            delay(3000)
                            _reDownloadStates.value = _reDownloadStates.value - modelId
                            false
                        }
                        else -> true
                    }
                }
                .collect()
        }
    }


}
