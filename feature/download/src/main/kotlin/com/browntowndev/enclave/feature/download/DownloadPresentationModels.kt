package com.browntowndev.enclave.feature.download

import androidx.compose.runtime.Immutable
import com.browntowndev.enclave.domain.model.config.LocalModelAsset
import com.browntowndev.enclave.domain.model.config.LocalModelConfiguration
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelId
import com.browntowndev.enclave.domain.model.config.LocalModelMetadata
import com.browntowndev.enclave.domain.model.config.UtilityType
import com.browntowndev.enclave.domain.model.download.DownloadModelsResult
import com.browntowndev.enclave.domain.model.download.DownloadSource
import com.browntowndev.enclave.domain.model.download.ModelScanResult
import com.browntowndev.enclave.domain.model.inference.ModelFileFormat
import com.browntowndev.enclave.domain.model.inference.ModelType

/**
 * Presentation mirror of domain [DownloadModelsResult] for [ModelDownloadScreen].
 * Enum and value-class fields are carried as their raw names/values so
 * [toDomain] reconstructs the domain result losslessly for the ViewModel.
 */
@Immutable
data class DownloadModelsResultUi(
    val allModels: Map<String, DownloadLocalModelAssetUi>,
    val utilityAssets: List<DownloadLocalModelAssetUi> = emptyList(),
    val modelsToDownload: List<DownloadLocalModelAssetUi>,
    val scanResult: ModelScanResultUi,
    val availableToRedownload: List<DownloadLocalModelAssetUi> = emptyList(),
    val imageGenerationPackMemberSha256s: Set<String> = emptySet(),
    val imageEditPackMemberSha256s: Set<String> = emptySet(),
)

@Immutable
data class ModelScanResultUi(
    val missingModels: List<DownloadLocalModelAssetUi>,
    val partialDownloads: Map<String, Long>,
    val allValid: Boolean,
    val directoryError: Boolean = false,
    val invalidModels: List<DownloadLocalModelAssetUi> = emptyList(),
)

@Immutable
data class DownloadLocalModelAssetUi(
    val metadata: DownloadLocalModelMetadataUi,
    val configurations: List<DownloadLocalModelConfigurationUi>,
)

@Immutable
data class DownloadLocalModelMetadataUi(
    val id: String,
    val displayName: String = "",
    val huggingFaceModelName: String,
    val huggingFacePath: String? = null,
    val remoteFileName: String,
    val localFileName: String,
    val sha256: String,
    val sizeInBytes: Long,
    val modelFileFormat: String,
    val source: String,
    val utilityType: String? = null,
    val isMultimodal: Boolean = false,
    val mmprojRemoteFileName: String? = null,
    val mmprojLocalFileName: String? = null,
    val mmprojSha256: String? = null,
    val mmprojSizeInBytes: Long? = null,
)

@Immutable
data class DownloadLocalModelConfigurationUi(
    val id: String = "",
    val localModelId: String,
    val displayName: String,
    val maxTokens: Int,
    val contextWindow: Int,
    val temperature: Double,
    val topP: Double,
    val topK: Int?,
    val minP: Double = 0.0,
    val repetitionPenalty: Double,
    val thinkingEnabled: Boolean = false,
    val reasoningBudget: Int,
    val systemPrompt: String,
    val isSystemPreset: Boolean = false,
    val defaultAssignments: List<String> = emptyList(),
)

fun DownloadModelsResult.toUi(): DownloadModelsResultUi =
    DownloadModelsResultUi(
        allModels = allModels.mapKeys { (type, _) -> type.name }.mapValues { (_, asset) -> asset.toUi() },
        utilityAssets = utilityAssets.map { it.toUi() },
        modelsToDownload = modelsToDownload.map { it.toUi() },
        scanResult = scanResult.toUi(),
        availableToRedownload = availableToRedownload.map { it.toUi() },
        imageGenerationPackMemberSha256s = imageGenerationPackMemberSha256s,
        imageEditPackMemberSha256s = imageEditPackMemberSha256s,
    )

fun DownloadModelsResultUi.toDomain(): DownloadModelsResult =
    DownloadModelsResult(
        allModels =
            allModels.mapKeys { (name, _) -> ModelType.valueOf(name) }
                .mapValues { (_, asset) -> asset.toDomain() },
        utilityAssets = utilityAssets.map { it.toDomain() },
        modelsToDownload = modelsToDownload.map { it.toDomain() },
        scanResult = scanResult.toDomain(),
        availableToRedownload = availableToRedownload.map { it.toDomain() },
        imageGenerationPackMemberSha256s = imageGenerationPackMemberSha256s,
        imageEditPackMemberSha256s = imageEditPackMemberSha256s,
    )

private fun ModelScanResult.toUi(): ModelScanResultUi =
    ModelScanResultUi(
        missingModels = missingModels.map { it.toUi() },
        partialDownloads = partialDownloads,
        allValid = allValid,
        directoryError = directoryError,
        invalidModels = invalidModels.map { it.toUi() },
    )

private fun ModelScanResultUi.toDomain(): ModelScanResult =
    ModelScanResult(
        missingModels = missingModels.map { it.toDomain() },
        partialDownloads = partialDownloads,
        allValid = allValid,
        directoryError = directoryError,
        invalidModels = invalidModels.map { it.toDomain() },
    )

private fun LocalModelAsset.toUi(): DownloadLocalModelAssetUi =
    DownloadLocalModelAssetUi(
        metadata = metadata.toUi(),
        configurations = configurations.map { it.toUi() },
    )

private fun DownloadLocalModelAssetUi.toDomain(): LocalModelAsset =
    LocalModelAsset(
        metadata = metadata.toDomain(),
        configurations = configurations.map { it.toDomain() },
    )

private fun LocalModelMetadata.toUi(): DownloadLocalModelMetadataUi =
    DownloadLocalModelMetadataUi(
        id = id.value,
        displayName = displayName,
        huggingFaceModelName = huggingFaceModelName,
        huggingFacePath = huggingFacePath,
        remoteFileName = remoteFileName,
        localFileName = localFileName,
        sha256 = sha256,
        sizeInBytes = sizeInBytes,
        modelFileFormat = modelFileFormat.name,
        source = source.name,
        utilityType = utilityType?.name,
        isMultimodal = isMultimodal,
        mmprojRemoteFileName = mmprojRemoteFileName,
        mmprojLocalFileName = mmprojLocalFileName,
        mmprojSha256 = mmprojSha256,
        mmprojSizeInBytes = mmprojSizeInBytes,
    )

private fun DownloadLocalModelMetadataUi.toDomain(): LocalModelMetadata =
    LocalModelMetadata(
        id = LocalModelId(id),
        displayName = displayName,
        huggingFaceModelName = huggingFaceModelName,
        huggingFacePath = huggingFacePath,
        remoteFileName = remoteFileName,
        localFileName = localFileName,
        sha256 = sha256,
        sizeInBytes = sizeInBytes,
        modelFileFormat = ModelFileFormat.valueOf(modelFileFormat),
        source = DownloadSource.valueOf(source),
        utilityType = utilityType?.let { UtilityType.valueOf(it) },
        isMultimodal = isMultimodal,
        mmprojRemoteFileName = mmprojRemoteFileName,
        mmprojLocalFileName = mmprojLocalFileName,
        mmprojSha256 = mmprojSha256,
        mmprojSizeInBytes = mmprojSizeInBytes,
    )

private fun LocalModelConfiguration.toUi(): DownloadLocalModelConfigurationUi =
    DownloadLocalModelConfigurationUi(
        id = id.value,
        localModelId = localModelId.value,
        displayName = displayName,
        maxTokens = maxTokens,
        contextWindow = contextWindow,
        temperature = temperature,
        topP = topP,
        topK = topK,
        minP = minP,
        repetitionPenalty = repetitionPenalty,
        thinkingEnabled = thinkingEnabled,
        reasoningBudget = reasoningBudget,
        systemPrompt = systemPrompt,
        isSystemPreset = isSystemPreset,
        defaultAssignments = defaultAssignments.map { it.name },
    )

private fun DownloadLocalModelConfigurationUi.toDomain(): LocalModelConfiguration =
    LocalModelConfiguration(
        id = LocalModelConfigurationId(id),
        localModelId = LocalModelId(localModelId),
        displayName = displayName,
        maxTokens = maxTokens,
        contextWindow = contextWindow,
        temperature = temperature,
        topP = topP,
        topK = topK,
        minP = minP,
        repetitionPenalty = repetitionPenalty,
        thinkingEnabled = thinkingEnabled,
        reasoningBudget = reasoningBudget,
        systemPrompt = systemPrompt,
        isSystemPreset = isSystemPreset,
        defaultAssignments = defaultAssignments.map { ModelType.valueOf(it) },
    )
