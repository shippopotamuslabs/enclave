package com.browntowndev.pocketcrew.testing

import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.LocalModelMetadata
import com.browntowndev.pocketcrew.domain.model.config.SlotResolvedLocalModel
import com.browntowndev.pocketcrew.domain.model.download.DownloadModelsResult
import com.browntowndev.pocketcrew.domain.model.download.DownloadProgressUpdate
import com.browntowndev.pocketcrew.domain.model.download.DownloadState
import com.browntowndev.pocketcrew.domain.model.download.DownloadStatus
import com.browntowndev.pocketcrew.domain.model.download.FileProgress
import com.browntowndev.pocketcrew.domain.model.download.ModelScanResult
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.download.DownloadSpeedTrackerPort
import com.browntowndev.pocketcrew.domain.port.download.ModelDownloadOrchestratorPort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.ApiModelRepositoryPort
import com.browntowndev.pocketcrew.domain.port.repository.ModelRegistryPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOf

class FakeModelDownloadOrchestrator : ModelDownloadOrchestratorPort {
    private val _downloadState = MutableStateFlow(DownloadState(status = DownloadStatus.CHECKING))
    override val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private val _snackbarMessages = kotlinx.coroutines.flow.MutableSharedFlow<String>()
    override val snackbarMessages: Flow<String> = _snackbarMessages.asSharedFlow()

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
            allModels = emptyMap(),
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
    private val _assets = MutableStateFlow<Map<ModelType, LocalModelAsset>>(emptyMap())
    private val _configs = MutableStateFlow<Map<ModelType, LocalModelConfiguration>>(emptyMap())

    override suspend fun getRegisteredAsset(modelType: ModelType): LocalModelAsset? = _assets.value[modelType]
    override suspend fun getRegisteredConfiguration(modelType: ModelType): LocalModelConfiguration? = _configs.value[modelType]
    
    override suspend fun getRegisteredAssets(): List<LocalModelAsset> = _assets.value.values.toList()
    override suspend fun getRegisteredConfigurations(): List<LocalModelConfiguration> = _configs.value.values.toList()

    override fun observeAsset(modelType: ModelType): Flow<LocalModelAsset?> = _assets.map { it[modelType] }
    override fun observeConfiguration(modelType: ModelType): Flow<LocalModelConfiguration?> = flowOf(null)
    override fun observeAssets(): Flow<List<LocalModelAsset>> = flowOf(emptyList())

    override suspend fun clearAll() {}

    override suspend fun upsertLocalAsset(asset: LocalModelAsset): Long = asset.metadata.id
    override suspend fun upsertLocalConfiguration(config: LocalModelConfiguration): Long = config.id
    override suspend fun setDefaultLocalConfig(modelType: ModelType, configId: Long) {}
    override suspend fun activateLocalModel(modelType: ModelType, asset: LocalModelAsset): SlotResolvedLocalModel {
        val selectedConfig = asset.configurations.firstOrNull()
            ?: throw IllegalArgumentException("Asset must contain at least one configuration")
        _assets.value = _assets.value + (modelType to asset)
        _configs.value = _configs.value + (modelType to selectedConfig)
        return SlotResolvedLocalModel(modelType, asset, selectedConfig)
    }
    override suspend fun getRegisteredSelection(modelType: ModelType): SlotResolvedLocalModel? {
        val asset = _assets.value[modelType] ?: return null
        val config = _configs.value[modelType] ?: return null
        return SlotResolvedLocalModel(modelType, asset, config)
    }
    override suspend fun saveLocalModelMetadata(metadata: LocalModelMetadata): Long = 0L
    override suspend fun deleteModel(modelId: Long, replacementLocalConfigId: Long?, replacementApiConfigId: Long?): Result<Unit> = Result.success(Unit)
    override suspend fun deleteLocalModelMetadata(id: Long) {}
    override suspend fun saveConfiguration(config: LocalModelConfiguration): Long = config.id
    override suspend fun deleteConfiguration(id: Long) {}

    override suspend fun getSoftDeletedModels(): List<LocalModelAsset> = emptyList()

    override suspend fun getAssetById(id: Long): LocalModelAsset? {
        return _assets.value.values.find { it.metadata.id == id }
    }
}

class FakeLoggingPort : LoggingPort {
    override fun debug(tag: String, message: String) {}
    override fun info(tag: String, message: String) {}
    override fun warning(tag: String, message: String) {}
    override fun error(tag: String, message: String, throwable: Throwable?) {}
    override fun recordException(tag: String, message: String, throwable: Throwable) {}
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
