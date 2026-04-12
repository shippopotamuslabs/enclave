package com.browntowndev.pocketcrew.feature.settings

import com.browntowndev.pocketcrew.domain.model.config.ApiCredentialsId
import com.browntowndev.pocketcrew.domain.model.config.ApiModelAsset
import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.DefaultModelAssignment
import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.OpenRouterRoutingConfiguration
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.inference.DiscoveredApiModel
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.inference.ModelSource
import com.browntowndev.pocketcrew.domain.usecase.settings.ReassignmentCandidate
import javax.inject.Inject

class ApiModelAssetUiMapper @Inject constructor() {
    fun map(asset: ApiModelAsset): ApiModelAssetUi = ApiModelAssetUi(
        credentialsId = asset.credentials.id,
        displayName = asset.credentials.displayName,
        provider = asset.credentials.provider,
        modelId = asset.credentials.modelId,
        baseUrl = asset.credentials.baseUrl ?: asset.credentials.provider.defaultBaseUrl(),
        isVision = asset.credentials.isVision,
        credentialAlias = asset.credentials.credentialAlias,
        configurations = asset.configurations.map(::mapConfig),
    )

    fun mapReusable(asset: ApiModelAsset): ReusableApiCredentialUi = ReusableApiCredentialUi(
        credentialsId = asset.credentials.id,
        displayName = asset.credentials.displayName,
        modelId = asset.credentials.modelId,
        credentialAlias = asset.credentials.credentialAlias,
    )

    fun mapConfig(config: ApiModelConfiguration): ApiModelConfigUi = ApiModelConfigUi(
        id = config.id,
        credentialsId = config.apiCredentialsId,
        displayName = config.displayName,
        maxTokens = config.maxTokens.toString(),
        contextWindow = config.contextWindow.toString(),
        temperature = config.temperature,
        topP = config.topP,
        topK = config.topK?.toString() ?: "40",
        minP = config.minP,
        frequencyPenalty = config.frequencyPenalty,
        presencePenalty = config.presencePenalty,
        customHeaders = config.customHeaders.map { CustomHeaderUi(it.key, it.value) },
        openRouterRouting = config.openRouterRouting,
        thinkingEnabled = false,
        systemPrompt = config.systemPrompt,
        reasoningEffort = config.reasoningEffort,
    )
}

class LocalModelAssetUiMapper @Inject constructor() {
    fun map(asset: LocalModelAsset): LocalModelAssetUi {
        val rawName = asset.metadata.huggingFaceModelName
        val parts = rawName.split("/")
        val providerName = if (parts.size >= 2) parts[0] else "Unknown"
        val modelNamePart = if (parts.size >= 2) parts.drop(1).joinToString("/") else rawName

        val format = when (asset.metadata.modelFileFormat) {
            ModelFileFormat.GGUF -> "GGUF"
            ModelFileFormat.LITERTLM -> "LiteRT"
            ModelFileFormat.TASK -> "Task"
        }

        val cleanName = modelNamePart
            .replace(Regex("-GGUF$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\.gguf$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\.litertlm$", RegexOption.IGNORE_CASE), "")
            .replace("-", " ")

        return LocalModelAssetUi(
            metadataId = asset.metadata.id,
            huggingFaceModelName = rawName,
            friendlyName = cleanName.ifBlank { rawName },
            providerName = providerName,
            format = format,
            remoteFileName = asset.metadata.remoteFileName,
            sizeInBytes = asset.metadata.sizeInBytes,
            configurations = asset.configurations.map(::mapConfig),
            visionCapable = asset.metadata.visionCapable,
            isSoftDeleted = asset.configurations.isEmpty(),
        )
    }

    fun mapConfig(config: LocalModelConfiguration): LocalModelConfigUi = LocalModelConfigUi(
        id = config.id,
        localModelId = config.localModelId,
        displayName = config.displayName,
        maxTokens = config.maxTokens.toString(),
        contextWindow = config.contextWindow.toString(),
        temperature = config.temperature,
        topP = config.topP,
        topK = config.topK?.toString() ?: "",
        minP = config.minP,
        repetitionPenalty = config.repetitionPenalty,
        systemPrompt = config.systemPrompt,
        thinkingEnabled = config.thinkingEnabled,
        isSystemPreset = config.isSystemPreset,
    )
}

class ApiDiscoveryUiFilter @Inject constructor() {
    fun filter(
        models: List<DiscoveredApiModelUi>,
        query: String,
        providerFilter: String?,
        sortOption: ModelSortOption,
    ): List<DiscoveredApiModelUi> = models
        .filter { model ->
            if (query.isBlank()) {
                true
            } else {
                model.name?.contains(query, ignoreCase = true) == true ||
                    model.modelId.contains(query, ignoreCase = true)
            }
        }
        .filter { model ->
            providerFilter == null || model.providerName == providerFilter
        }
        .sortedWith { a, b ->
            when (sortOption) {
                ModelSortOption.NEWEST -> {
                    val aCreated = a.created ?: 0L
                    val bCreated = b.created ?: 0L
                    if (aCreated != bCreated) {
                        bCreated.compareTo(aCreated)
                    } else {
                        (a.name ?: a.modelId).compareTo(b.name ?: b.modelId, ignoreCase = true)
                    }
                }

                ModelSortOption.PRICE_LOW_TO_HIGH -> {
                    val aHasPrice = a.promptPrice != null || a.completionPrice != null
                    val bHasPrice = b.promptPrice != null || b.completionPrice != null
                    when {
                        aHasPrice && bHasPrice -> {
                            val aPrice = (a.promptPrice ?: 0.0) + (a.completionPrice ?: 0.0)
                            val bPrice = (b.promptPrice ?: 0.0) + (b.completionPrice ?: 0.0)
                            if (aPrice != bPrice) {
                                aPrice.compareTo(bPrice)
                            } else {
                                (a.name ?: a.modelId).compareTo(b.name ?: b.modelId, ignoreCase = true)
                            }
                        }

                        aHasPrice -> -1
                        bHasPrice -> 1
                        else -> (a.name ?: a.modelId).compareTo(b.name ?: b.modelId, ignoreCase = true)
                    }
                }

                ModelSortOption.A_TO_Z -> {
                    (a.name ?: a.modelId).compareTo(b.name ?: b.modelId, ignoreCase = true)
                }
            }
        }
}

class ReassignmentOptionUiMapper @Inject constructor() {
    fun map(candidates: List<ReassignmentCandidate>): List<ReassignmentOptionUi> =
        candidates.map { candidate ->
            ReassignmentOptionUi(
                configId = candidate.configId,
                displayName = when (candidate.source) {
                    ModelSource.ON_DEVICE -> candidate.configDisplayName.ifBlank { candidate.assetDisplayName }
                    ModelSource.API -> {
                        if (candidate.assetDisplayName == candidate.configDisplayName) {
                            candidate.assetDisplayName
                        } else {
                            "${candidate.assetDisplayName} - ${candidate.configDisplayName}"
                        }
                    }
                },
                source = candidate.source,
                providerName = candidate.providerName,
                apiCredentialsId = candidate.apiCredentialsId,
                localModelId = candidate.localModelId,
            )
        }
}

internal fun DefaultModelAssignment.toUi(isVision: Boolean = false): DefaultModelAssignmentUi = DefaultModelAssignmentUi(
    modelType = modelType,
    source = if (apiConfigId != null) ModelSource.API else ModelSource.ON_DEVICE,
    currentModelName = displayName ?: "Unknown",
    displayLabel = modelType.displayLabel,
    providerName = providerName,
    presetName = presetName,
    isVision = isVision,
)

internal fun blankApiModelAssetDraft(): ApiModelAssetUi = ApiModelAssetUi(
    credentialsId = ApiCredentialsId(""),
    displayName = "",
    provider = com.browntowndev.pocketcrew.domain.model.inference.ApiProvider.ANTHROPIC,
    modelId = "",
    baseUrl = com.browntowndev.pocketcrew.domain.model.inference.ApiProvider.ANTHROPIC.defaultBaseUrl(),
    isVision = false,
    credentialAlias = "",
    configurations = emptyList(),
)

internal fun blankApiPresetDraft(
    openRouterRouting: OpenRouterRoutingConfiguration = OpenRouterRoutingConfiguration(),
): ApiModelConfigUi = ApiModelConfigUi(openRouterRouting = openRouterRouting)

internal fun DiscoveredApiModel.toUi(): DiscoveredApiModelUi {
    val provider = if (id.contains("/")) id.substringBefore("/") else null
    return DiscoveredApiModelUi(
        modelId = id,
        name = name,
        contextWindowTokens = contextWindowTokens,
        maxOutputTokens = maxOutputTokens,
        created = created,
        promptPrice = promptPrice,
        completionPrice = completionPrice,
        providerName = provider,
        visionCapable = visionCapable,
    )
}
