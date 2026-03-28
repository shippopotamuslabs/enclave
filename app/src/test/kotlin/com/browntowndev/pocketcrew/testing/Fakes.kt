/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.browntowndev.pocketcrew.testing
import com.browntowndev.pocketcrew.domain.model.config.ModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.ModelStatus
import com.browntowndev.pocketcrew.domain.model.download.DownloadModelsResult
import com.browntowndev.pocketcrew.domain.model.download.DownloadProgressUpdate
import com.browntowndev.pocketcrew.domain.model.download.DownloadState
import com.browntowndev.pocketcrew.domain.model.download.DownloadStatus
import com.browntowndev.pocketcrew.domain.model.download.FileProgress
import com.browntowndev.pocketcrew.domain.model.download.ModelScanResult
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.download.DownloadSpeedTrackerPort
import com.browntowndev.pocketcrew.domain.port.download.ModelDownloadOrchestratorPort
import com.browntowndev.pocketcrew.domain.port.repository.ModelRegistryPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map


class FakeModelDownloadOrchestrator : ModelDownloadOrchestratorPort {
    private val _downloadState = MutableStateFlow(DownloadState(status = DownloadStatus.CHECKING))
    override val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private val _speedTracker = MutableStateFlow<FakeSpeedTracker?>(null)
    override val speedTracker: DownloadSpeedTrackerPort
        get() = _speedTracker.value ?: FakeSpeedTracker().also { _speedTracker.value = it }

    private var _lastStartDownloadsParams: Pair<DownloadModelsResult, Boolean>? = null
    val lastStartDownloadsParams: Pair<DownloadModelsResult, Boolean>?
        get() = _lastStartDownloadsParams

    private var _lastModelsResult: DownloadModelsResult? = null
    val lastModelsResult: DownloadModelsResult?
        get() = _lastModelsResult

    fun setDownloadState(state: DownloadState) {
        _downloadState.value = state
    }

    fun emitStatus(status: DownloadStatus) {
        _downloadState.value = _downloadState.value.copy(status = status)
    }

    fun emitProgress(progress: Float, modelsComplete: Int = 0, modelsTotal: Int = 0) {
        _downloadState.value = _downloadState.value.copy(
            overallProgress = progress,
            modelsComplete = modelsComplete,
            modelsTotal = modelsTotal
        )
    }

    fun emitError(message: String) {
        _downloadState.value = _downloadState.value.copy(
            status = DownloadStatus.ERROR,
            errorMessage = message
        )
    }

    fun emitCurrentDownloads(downloads: List<FileProgress>) {
        _downloadState.value = _downloadState.value.copy(currentDownloads = downloads)
    }

    fun reset() {
        _downloadState.value = DownloadState(status = DownloadStatus.CHECKING)
        _lastStartDownloadsParams = null
        _lastModelsResult = null
    }

    override fun initializeWithStartupResult(result: DownloadModelsResult) {
        _lastModelsResult = result
        _downloadState.value = when {
            result.modelsToDownload.isEmpty() -> DownloadState(status = DownloadStatus.READY)
            else -> DownloadState(status = DownloadStatus.IDLE)
        }
    }

    override suspend fun startDownloads(modelsResult: DownloadModelsResult, wifiOnly: Boolean): Boolean {
        _lastStartDownloadsParams = modelsResult to wifiOnly
        _lastModelsResult = modelsResult
        _downloadState.value = DownloadState(
            status = DownloadStatus.DOWNLOADING,
            modelsTotal = modelsResult.modelsToDownload.size
        )
        return true
    }

    override suspend fun startDownloads(wifiOnly: Boolean): Boolean {
        val modelsResult = _lastModelsResult ?: DownloadModelsResult(
            modelsToDownload = emptyList(),
            scanResult = ModelScanResult(
                missingModels = emptyList(),
                partialDownloads = emptyMap(),
                allValid = true
            )
        )
        return startDownloads(modelsResult, wifiOnly)
    }

    override suspend fun updateFromProgressUpdate(progressUpdate: DownloadProgressUpdate) { /* no-op */ }

    override fun pauseDownloads() {
        _downloadState.value = _downloadState.value.copy(status = DownloadStatus.PAUSED)
    }

    override suspend fun resumeDownloads() {
        _downloadState.value = _downloadState.value.copy(status = DownloadStatus.DOWNLOADING)
    }

    override suspend fun cancelDownloads() {
        _downloadState.value = DownloadState(status = DownloadStatus.IDLE)
    }

    override suspend fun retryFailed() {
        _downloadState.value = DownloadState(status = DownloadStatus.DOWNLOADING)
    }

    override suspend fun downloadOnMobileData() {
        _downloadState.value = _downloadState.value.copy(status = DownloadStatus.DOWNLOADING)
    }

    override fun setError(message: String) {
        emitError(message)
    }
}

class FakeModelRegistry : ModelRegistryPort {
    private val _registeredModels = MutableStateFlow<Map<ModelType, ModelConfiguration>>(emptyMap())
    private val _modelsCache = mutableMapOf<ModelType, ModelConfiguration>()

    fun registerModel(modelType: ModelType, config: ModelConfiguration) {
        _modelsCache[modelType] = config
        _registeredModels.value = _modelsCache.toMap()
    }

    fun clearModels() {
        _modelsCache.clear()
        _registeredModels.value = emptyMap()
    }

    fun getConfig(modelType: ModelType): ModelConfiguration? = _modelsCache[modelType]

    override suspend fun getRegisteredModel(modelType: ModelType): ModelConfiguration? = _modelsCache[modelType]
    override fun getRegisteredModelSync(modelType: ModelType): ModelConfiguration? = _modelsCache[modelType]
    override suspend fun getRegisteredModels(): List<ModelConfiguration> = _modelsCache.values.toList()
    override fun getRegisteredModelsSync(): List<ModelConfiguration> = _modelsCache.values.toList()
    override fun observeRegisteredModels(): Flow<Map<ModelType, String>> = _registeredModels.map { map -> map.mapValues { it.value.metadata.localFileName } }
    override fun observeModel(modelType: ModelType): Flow<ModelConfiguration?> = _registeredModels.map { it[modelType] }

    override suspend fun setRegisteredModel(config: ModelConfiguration, status: ModelStatus, markExistingAsOld: Boolean) {
        registerModel(config.modelType, config)
    }

    override suspend fun clearAll() { clearModels() }
    override suspend fun clearOld() { /* no-op */ }
    override suspend fun getModelsPreferringOld(): List<ModelConfiguration> = _modelsCache.values.toList()
}

class FakeSpeedTracker : DownloadSpeedTrackerPort {
    override fun calculateSpeedAndEta(filename: String, bytesDownloaded: Long, totalSize: Long): Pair<Double, Long> = 0.0 to -1L
    override fun calculateAggregateSpeedAndEta(totalBytesDownloaded: Long, totalSize: Long): Pair<Double, Long> = 0.0 to -1L
    override fun formatEta(seconds: Long): String = when {
        seconds < 0 -> "Calculating..."
        seconds < 60 -> "< 1 min"
        seconds < 3600 -> "${seconds / 60} min"
        else -> "${seconds / 3600.0} hours"
    }
    override fun clear(filename: String) { /* no-op */ }
    override fun clearAll() { /* no-op */ }
}
