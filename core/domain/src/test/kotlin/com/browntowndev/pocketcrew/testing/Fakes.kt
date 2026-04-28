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
import com.browntowndev.pocketcrew.domain.model.config.ApiCredentials
import com.browntowndev.pocketcrew.domain.model.config.ApiCredentialsId
import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.DefaultModelAssignment
import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelMetadata
import com.browntowndev.pocketcrew.domain.model.config.MediaProviderId
import com.browntowndev.pocketcrew.domain.model.config.SlotResolvedLocalModel
import com.browntowndev.pocketcrew.domain.model.config.TtsProviderId
import com.browntowndev.pocketcrew.domain.model.download.DownloadModelsResult
import com.browntowndev.pocketcrew.domain.model.download.DownloadProgressUpdate
import com.browntowndev.pocketcrew.domain.model.download.DownloadState
import com.browntowndev.pocketcrew.domain.model.download.DownloadStatus
import com.browntowndev.pocketcrew.domain.model.download.FileProgress
import com.browntowndev.pocketcrew.domain.model.download.ModelScanResult
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.download.DownloadSpeedTrackerPort
import com.browntowndev.pocketcrew.domain.port.download.ModelDownloadOrchestratorPort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.ApiModelRepositoryPort
import com.browntowndev.pocketcrew.domain.port.repository.DefaultModelRepositoryPort
import com.browntowndev.pocketcrew.domain.port.repository.LocalModelRepositoryPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOf


fun createFakeLocalModelAsset(
    id: LocalModelId = LocalModelId("0"),
    huggingFaceModelName: String = "test/model",
    remoteFileName: String = "model.bin",
    localFileName: String = "model.bin",
    sha256: String = "abc123",
    sizeInBytes: Long = 1024,
    modelFileFormat: ModelFileFormat = ModelFileFormat.LITERTLM,
    configurations: List<LocalModelConfiguration> = emptyList()
) = LocalModelAsset(
    metadata = LocalModelMetadata(
        id = id,
        huggingFaceModelName = huggingFaceModelName,
        remoteFileName = remoteFileName,
        localFileName = localFileName,
        sha256 = sha256,
        sizeInBytes = sizeInBytes,
        modelFileFormat = modelFileFormat
    ),
    configurations = configurations
)

fun createFakeLocalModelConfiguration(
    id: LocalModelConfigurationId = LocalModelConfigurationId(""),
    localModelId: LocalModelId = LocalModelId("0"),
    displayName: String = "Default Configuration",
    maxTokens: Int = 2048,
    contextWindow: Int = 2048,
    temperature: Double = 0.7,
    topP: Double = 0.95,
    topK: Int? = 40,
    repetitionPenalty: Double = 1.0,
    systemPrompt: String = "You are a helpful assistant.",
    isSystemPreset: Boolean = false
) = LocalModelConfiguration(
    id = id,
    localModelId = localModelId,
    displayName = displayName,
    maxTokens = maxTokens,
    contextWindow = contextWindow,
    temperature = temperature,
    topP = topP,
    topK = topK,
    repetitionPenalty = repetitionPenalty,
    systemPrompt = systemPrompt,
    isSystemPreset = isSystemPreset
)


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
            allModels = emptyMap(),
            modelsToDownload = emptyList(),
            scanResult = ModelScanResult(
                missingModels = emptyList(),
                partialDownloads = emptyMap(),
                allValid = true
            ),
            availableToRedownload = emptyList()
        )
        return startDownloads(modelsResult, wifiOnly)
    }

    override suspend fun updateFromProgressUpdate(progressUpdate: DownloadProgressUpdate) {}

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

class FakeLocalModelRepository : LocalModelRepositoryPort {
    private val _assets = MutableStateFlow<Map<LocalModelId, LocalModelAsset>>(emptyMap())

    fun registerAsset(modelType: ModelType, asset: LocalModelAsset) {
        _assets.value = _assets.value + (asset.metadata.id to asset)
    }

    fun clearModels() {
        _assets.value = emptyMap()
    }

    override suspend fun getAllLocalAssets(): List<LocalModelAsset> = _assets.value.values.toList()
    override fun observeAllLocalAssets(): Flow<List<LocalModelAsset>> = flowOf(emptyList())
    override suspend fun getAssetByConfigId(configId: LocalModelConfigurationId): LocalModelAsset? = null

    override suspend fun clearAll() { clearModels() }

    override suspend fun upsertLocalAsset(asset: LocalModelAsset): LocalModelId = asset.metadata.id
    override suspend fun upsertLocalConfiguration(config: LocalModelConfiguration): LocalModelConfigurationId = config.id

    override suspend fun saveLocalModelMetadata(metadata: LocalModelMetadata): LocalModelId = metadata.id

    override suspend fun deleteLocalModelMetadata(id: LocalModelId) {
        val entryToDelete = _assets.value.entries.find { it.value.metadata.id == id }
        if (entryToDelete != null) {
            _assets.value = _assets.value - entryToDelete.key
        }
    }

    private val _configs = MutableStateFlow<Map<LocalModelConfigurationId, LocalModelConfiguration>>(emptyMap())
 
    override suspend fun saveConfiguration(config: LocalModelConfiguration): LocalModelConfigurationId {
        _configs.value = _configs.value + (config.id to config)
        return config.id
    }
 
    override suspend fun deleteConfiguration(id: LocalModelConfigurationId) {
        _configs.value = _configs.value - id
    }
 
    override suspend fun getConfigurationById(id: LocalModelConfigurationId): LocalModelConfiguration? {
        return _configs.value[id]
    }

    override suspend fun getAllConfigurationsForAsset(localModelId: LocalModelId): List<LocalModelConfiguration> {
        return _configs.value.values.filter { it.localModelId == localModelId }
    }

    override suspend fun deleteAllConfigurationsForAsset(localModelId: LocalModelId) {
        val keysToRemove = _configs.value.entries.filter { it.value.localModelId == localModelId }.map { it.key }
        _configs.value = _configs.value - keysToRemove.toSet()
    }

    override suspend fun getSoftDeletedModels(): List<LocalModelAsset> = emptyList()

    override suspend fun getAssetById(id: LocalModelId): LocalModelAsset? {
        return _assets.value.values.find { it.metadata.id == id }
    }

    override suspend fun restoreSoftDeletedModel(
        id: LocalModelId,
        configurations: List<LocalModelConfiguration>
    ): LocalModelAsset {
        val asset = getAssetById(id) ?: throw NoSuchElementException("Model $id not found")
        val restoredAsset = asset.copy(configurations = configurations)
        _assets.value = _assets.value + (id to restoredAsset)
        return restoredAsset
    }
}

class FakeApiModelRepository : ApiModelRepositoryPort {
    private val _credentials = MutableStateFlow<List<ApiCredentials>>(emptyList())
    private val _configs = MutableStateFlow<List<ApiModelConfiguration>>(emptyList())
    private var nextCredId = 1
    val savedKeys = mutableMapOf<ApiCredentialsId, String>()

    override fun observeAllCredentials(): Flow<List<ApiCredentials>> = _credentials
    override fun observeAllConfigurations(): Flow<List<ApiModelConfiguration>> = _configs
    override suspend fun getAllCredentials(): List<ApiCredentials> = _credentials.value
    override suspend fun getCredentialsById(id: ApiCredentialsId): ApiCredentials? = _credentials.value.find { it.id == id }
    override suspend fun findMatchingCredentials(
        provider: ApiProvider,
        modelId: String,
        baseUrl: String?,
        apiKey: String,
        sourceCredentialAlias: String?,
    ): ApiCredentials? {
        val resolvedApiKey = when {
            apiKey.isNotBlank() -> apiKey
            !sourceCredentialAlias.isNullOrBlank() -> _credentials.value
                .firstOrNull { it.credentialAlias == sourceCredentialAlias }
                ?.let { savedKeys[it.id] }
            else -> null
        } ?: return null

        return _credentials.value.firstOrNull { credential ->
            credential.provider == provider &&
                credential.modelId == modelId &&
                credential.baseUrl.normalizedBaseUrl() == baseUrl.normalizedBaseUrl() &&
                savedKeys[credential.id] == resolvedApiKey
        }
    }

    override suspend fun saveCredentials(
        credentials: ApiCredentials,
        apiKey: String,
        sourceCredentialAlias: String?
    ): ApiCredentialsId {
        val id = if (credentials.id.value.isEmpty()) ApiCredentialsId((nextCredId++).toString()) else credentials.id
        val saved = credentials.copy(id = id)
        _credentials.value = _credentials.value.filter { it.id != id } + saved
        savedKeys[id] = if (apiKey.isNotBlank()) {
            apiKey
        } else {
            sourceCredentialAlias?.let { alias ->
                _credentials.value
                    .firstOrNull { it.credentialAlias == alias }
                    ?.id
                    ?.let(savedKeys::get)
                    .orEmpty()
            }.orEmpty()
        }
        return id
    }

    override suspend fun deleteCredentials(id: ApiCredentialsId) {
        _credentials.value = _credentials.value.filter { it.id != id }
        savedKeys.remove(id)
        deleteConfigurationsForCredentials(id)
    }

    override suspend fun getConfigurationsForCredentials(credentialsId: ApiCredentialsId): List<ApiModelConfiguration> =
        _configs.value.filter { it.apiCredentialsId == credentialsId }

    override suspend fun getConfigurationById(id: ApiModelConfigurationId): ApiModelConfiguration? =
        _configs.value.find { it.id == id }
 
    override suspend fun saveConfiguration(config: ApiModelConfiguration): ApiModelConfigurationId {
        val id = if (config.id.value.isEmpty()) ApiModelConfigurationId(java.util.UUID.randomUUID().toString()) else config.id
        val saved = config.copy(id = id)
        _configs.value = _configs.value.filter { it.id != id } + saved
        return id
    }
 
    override suspend fun deleteConfigurationsForCredentials(credentialsId: ApiCredentialsId) {
        _configs.value = _configs.value.filter { it.apiCredentialsId != credentialsId }
    }
 
    override suspend fun deleteConfiguration(id: ApiModelConfigurationId) {
        _configs.value = _configs.value.filter { it.id != id }
    }

    private fun String?.normalizedBaseUrl(): String = this?.trim().orEmpty()
}

class FakeDefaultModelRepository : DefaultModelRepositoryPort {
    private val _defaults = MutableStateFlow<List<DefaultModelAssignment>>(emptyList())

    override suspend fun getDefault(modelType: ModelType): DefaultModelAssignment? =
        _defaults.value.find { it.modelType == modelType }

    override fun observeDefaults(): Flow<List<DefaultModelAssignment>> = _defaults

    override suspend fun setDefault(
        modelType: ModelType,
        localConfigId: LocalModelConfigurationId?,
        apiConfigId: ApiModelConfigurationId?,
        ttsProviderId: TtsProviderId?,
        mediaProviderId: MediaProviderId?
    ) {
        val assignment = DefaultModelAssignment(modelType, localConfigId, apiConfigId, ttsProviderId, mediaProviderId)
        _defaults.value = _defaults.value.filter { it.modelType != modelType } + assignment
    }

    override suspend fun clearDefault(modelType: ModelType) {
        _defaults.value = _defaults.value.filter { it.modelType != modelType }
    }

    fun seed(assignments: List<DefaultModelAssignment>) {
        _defaults.value = assignments
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
    override fun clear(filename: String) {}
    override fun clearAll() {}
}
