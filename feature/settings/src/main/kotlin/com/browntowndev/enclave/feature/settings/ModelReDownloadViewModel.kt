package com.browntowndev.enclave.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.browntowndev.enclave.domain.model.config.LocalModelId
import com.browntowndev.enclave.domain.model.download.DownloadWorkLifecycle
import com.browntowndev.enclave.domain.model.download.DownloadWorkSnapshot
import com.browntowndev.enclave.domain.usecase.download.ObserveDownloadWorkUseCase
import com.browntowndev.enclave.domain.usecase.modelconfig.ReDownloadModelPackUseCase
import com.browntowndev.enclave.domain.usecase.modelconfig.ReDownloadModelUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ModelReDownloadViewModel
    @Inject
    constructor(
        private val reDownloadModelUseCase: ReDownloadModelUseCase,
        private val reDownloadModelPackUseCase: ReDownloadModelPackUseCase,
        private val observeDownloadWorkUseCase: ObserveDownloadWorkUseCase,
    ) : ViewModel() {
        private companion object {
            const val SUCCESS_DELAY_MS = 2000L
            const val FAILURE_DELAY_MS = 3000L
        }

        private val _reDownloadStates = MutableStateFlow<Map<String, ReDownloadProgressUi>>(emptyMap())
        val reDownloadStates: StateFlow<Map<String, ReDownloadProgressUi>> = _reDownloadStates.asStateFlow()

        private val _packReDownloadStates = MutableStateFlow<Map<String, ReDownloadProgressUi>>(emptyMap())
        val packReDownloadStates: StateFlow<Map<String, ReDownloadProgressUi>> = _packReDownloadStates.asStateFlow()

        fun reDownloadModel(modelId: String) {
            viewModelScope.launch {
                _reDownloadStates.value = _reDownloadStates.value + (modelId to ReDownloadProgressUi.Preparing)

                reDownloadModelUseCase(LocalModelId(modelId))
                    .onSuccess {
                        observeDownloadProgress(modelId)
                    }.onFailure { error ->
                        _reDownloadStates.value = _reDownloadStates.value +
                            (modelId to ReDownloadProgressUi.Failed(error.message ?: "Unknown error"))
                        delay(FAILURE_DELAY_MS)
                        _reDownloadStates.value = _reDownloadStates.value - modelId
                    }
            }
        }

        fun reDownloadPack(packId: String) {
            viewModelScope.launch {
                _packReDownloadStates.update { it + (packId to ReDownloadProgressUi.Preparing) }

                reDownloadModelPackUseCase(packId)
                    .onSuccess { scheduled ->
                        if (scheduled.isEmpty()) {
                            _packReDownloadStates.update { it - packId }
                        } else {
                            observePackDownloadProgress(packId)
                        }
                    }.onFailure { error ->
                        _packReDownloadStates.update {
                            it + (packId to ReDownloadProgressUi.Failed(error.message ?: "Unknown error"))
                        }
                        delay(FAILURE_DELAY_MS)
                        _packReDownloadStates.update { it - packId }
                    }
            }
        }

        private fun observePackDownloadProgress(packId: String) {
            viewModelScope.launch {
                var awaitingFinalizer = false
                observeDownloadWorkUseCase()
                    .takeWhile { workInfo ->
                        if (workInfo == null) {
                            if (awaitingFinalizer) {
                                markPackComplete(packId)
                            } else {
                                _packReDownloadStates.update { it - packId }
                            }
                            return@takeWhile false
                        }

                        when {
                            workInfo.isTerminalSuccess -> {
                                markPackComplete(packId)
                                return@takeWhile false
                            }
                            workInfo.isTerminalFailure -> {
                                markPackFailed(packId)
                                return@takeWhile false
                            }
                            workInfo.isIntermediateDownloadSuccess -> {
                                awaitingFinalizer = true
                                updatePackFinalizing(workInfo, packId)
                            }
                            workInfo.isFinalizing -> {
                                awaitingFinalizer = true
                                updatePackFinalizing(workInfo, packId)
                            }
                            workInfo.lifecycle == DownloadWorkLifecycle.RUNNING ||
                                workInfo.lifecycle == DownloadWorkLifecycle.ENQUEUED ||
                                workInfo.lifecycle == DownloadWorkLifecycle.BLOCKED -> {
                                updatePackDownloading(workInfo, packId)
                            }
                        }
                        true
                    }.collect()
            }
        }

        private fun observeDownloadProgress(modelId: String) {
            viewModelScope.launch {
                var awaitingFinalizer = false
                observeDownloadWorkUseCase()
                    .takeWhile { workInfo ->
                        if (workInfo == null) {
                            if (awaitingFinalizer) {
                                markModelComplete(modelId)
                            } else {
                                _reDownloadStates.value = _reDownloadStates.value - modelId
                            }
                            return@takeWhile false
                        }

                        val modelProgress = workInfo.unassignedFileProgress

                        when {
                            workInfo.isTerminalSuccess -> {
                                markModelComplete(modelId)
                                return@takeWhile false
                            }
                            workInfo.isTerminalFailure -> {
                                markModelFailed(modelId)
                                return@takeWhile false
                            }
                            workInfo.isIntermediateDownloadSuccess ||
                                workInfo.isFinalizing -> {
                                awaitingFinalizer = true
                                val extractProgress = workInfo.postProcessProgress
                                _reDownloadStates.value = _reDownloadStates.value +
                                    (modelId to ReDownloadProgressUi.Finalizing(extractProgress))
                            }
                            modelProgress != null -> {
                                val parts = modelProgress.split("|")
                                if (parts.size >= 3) {
                                    val downloaded = parts[1].toLongOrNull() ?: 0L
                                    val total = parts[2].toLongOrNull() ?: 1L
                                    val progress = if (total > 0) downloaded.toFloat() / total else 0f
                                    _reDownloadStates.value = _reDownloadStates.value +
                                        (modelId to ReDownloadProgressUi.Downloading(progress))
                                }
                            }
                        }
                        true
                    }.collect()
            }
        }

        private suspend fun markPackComplete(packId: String) {
            _packReDownloadStates.update { it + (packId to ReDownloadProgressUi.Complete) }
            delay(SUCCESS_DELAY_MS)
            _packReDownloadStates.update { it - packId }
        }

        private suspend fun markPackFailed(packId: String) {
            _packReDownloadStates.update { it + (packId to ReDownloadProgressUi.Failed("Download failed")) }
            delay(FAILURE_DELAY_MS)
            _packReDownloadStates.update { it - packId }
        }

        private suspend fun markModelComplete(modelId: String) {
            _reDownloadStates.value = _reDownloadStates.value + (modelId to ReDownloadProgressUi.Complete)
            delay(SUCCESS_DELAY_MS)
            _reDownloadStates.value = _reDownloadStates.value - modelId
        }

        private suspend fun markModelFailed(modelId: String) {
            _reDownloadStates.value = _reDownloadStates.value +
                (modelId to ReDownloadProgressUi.Failed("Download failed"))
            delay(FAILURE_DELAY_MS)
            _reDownloadStates.value = _reDownloadStates.value - modelId
        }

        private fun updatePackDownloading(
            workInfo: DownloadWorkSnapshot,
            packId: String,
        ) {
            val aggregate = workInfo.aggregateProgress ?: return
            _packReDownloadStates.update { it + (packId to ReDownloadProgressUi.Downloading(aggregate)) }
        }

        private fun updatePackFinalizing(
            workInfo: DownloadWorkSnapshot,
            packId: String,
        ) {
            val extractProgress = workInfo.postProcessProgress
            _packReDownloadStates.update { it + (packId to ReDownloadProgressUi.Finalizing(extractProgress)) }
        }
    }
