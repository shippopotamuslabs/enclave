package com.browntowndev.enclave.feature.settings

import com.browntowndev.enclave.domain.model.config.ApiCredentialsId
import com.browntowndev.enclave.domain.model.config.ApiModelAsset
import com.browntowndev.enclave.domain.model.config.ApiModelConfiguration
import com.browntowndev.enclave.domain.model.config.ApiModelPricingFormat
import com.browntowndev.enclave.domain.model.config.DefaultModelAssignment
import com.browntowndev.enclave.domain.model.config.LocalModelAsset
import com.browntowndev.enclave.domain.model.config.LocalModelConfiguration
import com.browntowndev.enclave.domain.model.config.OpenRouterRoutingConfiguration
import com.browntowndev.enclave.domain.model.error.ActiveModelConfigurationDecryptException
import com.browntowndev.enclave.domain.model.inference.DiscoveredApiModel
import com.browntowndev.enclave.domain.model.inference.ModelFileFormat
import com.browntowndev.enclave.domain.model.inference.ModelSource
import com.browntowndev.enclave.domain.usecase.settings.ReassignmentCandidate
import javax.inject.Inject

class ApiModelAssetUiMapper
    @Inject
    constructor() {
        fun map(asset: ApiModelAsset): ApiModelAssetUi =
            ApiModelAssetUi(
                credentialsId = asset.credentials.id,
                displayName = asset.credentials.displayName,
                provider = asset.credentials.provider,
                baseUrl = asset.credentials.baseUrl ?: asset.credentials.provider.defaultBaseUrl(),
                credentialAlias = asset.credentials.credentialAlias,
                configurations = asset.configurations.map(::mapConfig),
            )

        fun mapReusable(asset: ApiModelAsset): ReusableApiCredentialUi =
            ReusableApiCredentialUi(
                credentialsId = asset.credentials.id,
                displayName = asset.credentials.displayName,
                providerLabel = asset.credentials.provider.displayName,
                credentialAlias = asset.credentials.credentialAlias,
            )

        fun mapConfig(config: ApiModelConfiguration): ApiModelConfigUi =
            ApiModelConfigUi(
                id = config.id,
                credentialsId = config.apiCredentialsId,
                modelId = config.modelId,
                isMultimodal = config.isMultimodal,
                displayName = config.displayName,
                maxTokens = config.maxTokens.toString(),
                contextWindow = config.contextWindow.toString(),
                temperature = config.temperature,
                topP = config.topP,
                topK = config.topK?.toString() ?: "40",
                minP = config.minP,
                frequencyPenalty = config.frequencyPenalty,
                presencePenalty = config.presencePenalty,
                maxTokensDisabled = config.maxTokensDisabled,
                temperatureDisabled = config.temperatureDisabled,
                topPDisabled = config.topPDisabled,
                topKDisabled = config.topKDisabled,
                minPDisabled = config.minPDisabled,
                frequencyPenaltyDisabled = config.frequencyPenaltyDisabled,
                presencePenaltyDisabled = config.presencePenaltyDisabled,
                customHeaders = config.customHeaders.map { CustomHeaderUi(it.key, it.value) },
                openRouterRouting = config.openRouterRouting,
                thinkingEnabled = false,
                systemPrompt = config.systemPrompt,
                reasoningEffort = config.reasoningEffort,
                promptPriceUsdPerMillion =
                    config.pricing.promptUsdPerMillion
                        ?.let(ApiModelPricingFormat::formatUsdPerMillion)
                        .orEmpty(),
                completionPriceUsdPerMillion =
                    config.pricing.completionUsdPerMillion
                        ?.let(ApiModelPricingFormat::formatUsdPerMillion)
                        .orEmpty(),
                cachedPromptPriceUsdPerMillion =
                    config.pricing.cachedPromptUsdPerMillion
                        ?.let(ApiModelPricingFormat::formatUsdPerMillion)
                        .orEmpty(),
            )
    }

private object LocalModelAssetConstants {
    val dashGgufRegex = Regex("-GGUF$", RegexOption.IGNORE_CASE)
    val dotGgufRegex = Regex("\\.gguf$", RegexOption.IGNORE_CASE)
    val dotLitertlmRegex = Regex("\\.litertlm$", RegexOption.IGNORE_CASE)
    val dotBinRegex = Regex("\\.bin$", RegexOption.IGNORE_CASE)
    val dotOnnxRegex = Regex("\\.onnx$", RegexOption.IGNORE_CASE)
    val dotTxtRegex = Regex("\\.txt$", RegexOption.IGNORE_CASE)
}

class LocalModelAssetUiMapper
    @Inject
    constructor() {
        fun map(asset: LocalModelAsset): LocalModelAssetUi {
            val rawName = asset.metadata.huggingFaceModelName
            val parts = rawName.split("/")
            val providerName = if (parts.size >= 2) parts[0] else "Unknown"
            val modelNamePart = if (parts.size >= 2) parts.drop(1).joinToString("/") else rawName

            val format =
                when (asset.metadata.modelFileFormat) {
                    ModelFileFormat.GGUF -> "GGUF"
                    ModelFileFormat.LITERTLM, ModelFileFormat.LITERT_ALIAS -> "LiteRT"
                    ModelFileFormat.BIN -> "BIN"
                    ModelFileFormat.ONNX -> "ONNX"
                    ModelFileFormat.TXT -> "TXT"
                }

            val cleanName =
                modelNamePart
                    .replace(LocalModelAssetConstants.dashGgufRegex, "")
                    .replace(LocalModelAssetConstants.dotGgufRegex, "")
                    .replace(LocalModelAssetConstants.dotLitertlmRegex, "")
                    .replace(LocalModelAssetConstants.dotBinRegex, "")
                    .replace(LocalModelAssetConstants.dotOnnxRegex, "")
                    .replace(LocalModelAssetConstants.dotTxtRegex, "")
                    .replace("-", " ")

            val friendlyName =
                asset.metadata.displayName.ifBlank {
                    cleanName.ifBlank { rawName }
                }

            return LocalModelAssetUi(
                metadataId = asset.metadata.id,
                huggingFaceModelName = rawName,
                friendlyName = friendlyName,
                providerName = providerName,
                format = format,
                remoteFileName = asset.metadata.remoteFileName,
                sizeInBytes = asset.metadata.sizeInBytes,
                configurations = asset.configurations.map(::mapConfig),
                isMultimodal = asset.metadata.isMultimodal,
                isSoftDeleted = asset.configurations.isEmpty(),
            )
        }

        fun mapConfig(config: LocalModelConfiguration): LocalModelConfigUi =
            LocalModelConfigUi(
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

class ApiDiscoveryUiFilter
    @Inject
    constructor() {
        @Suppress("CyclomaticComplexMethod")
        fun filter(
            models: List<DiscoveredApiModelUi>,
            query: String,
            providerFilters: Set<String>,
            sortOption: ModelSortOption,
            requireChatCapability: Boolean = false,
        ): List<DiscoveredApiModelUi> =
            models
                .filter { model ->
                    if (requireChatCapability && !model.isChatCapable) {
                        false
                    } else if (query.isBlank()) {
                        true
                    } else {
                        model.name?.contains(query, ignoreCase = true) == true ||
                            model.modelId.contains(query, ignoreCase = true)
                    }
                }.filter { model ->
                    providerFilters.isEmpty() || providerFilters.contains(model.providerName)
                }.sortedWith { a, b ->
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

class ReassignmentOptionUiMapper
    @Inject
    constructor() {
        fun map(candidates: List<ReassignmentCandidate>): List<ReassignmentOptionUi> =
            candidates.map { candidate ->
                ReassignmentOptionUi(
                    configId = candidate.configId,
                    displayName =
                        when (candidate.source) {
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

internal fun DefaultModelAssignment.toUi(isMultimodal: Boolean = false): DefaultModelAssignmentUi =
    DefaultModelAssignmentUi(
        modelType = modelType,
        source =
            when {
                apiConfigId != null -> ModelSource.API
                localConfigId != null -> ModelSource.ON_DEVICE
                ttsProviderId != null -> ModelSource.API // TTS is also API-based for now
                else -> ModelSource.API
            },
        currentModelName =
            displayName
                ?: when {
                    localConfigId != null ||
                        apiConfigId != null ||
                        ttsProviderId != null ||
                        mediaProviderId != null ->
                        ActiveModelConfigurationDecryptException.USER_MESSAGE
                    else -> "None Assigned"
                },
        displayLabel = modelType.displayLabel,
        providerName = providerName,
        presetName =
            presetName
                ?: when {
                    localConfigId != null ||
                        apiConfigId != null ||
                        ttsProviderId != null ->
                        ActiveModelConfigurationDecryptException.USER_MESSAGE
                    else -> null
                },
        modelId = modelId,
        isMultimodal = isMultimodal,
    )

internal fun blankApiModelAssetDraft(): ApiModelAssetUi =
    ApiModelAssetUi(
        credentialsId = ApiCredentialsId(""),
        displayName = "",
        provider = com.browntowndev.enclave.domain.model.inference.ApiProvider.ANTHROPIC,
        baseUrl =
            com.browntowndev.enclave.domain.model.inference.ApiProvider.ANTHROPIC
                .defaultBaseUrl(),
        credentialAlias = "",
        configurations = emptyList(),
    )

internal fun blankApiPresetDraft(openRouterRouting: OpenRouterRoutingConfiguration = OpenRouterRoutingConfiguration()): ApiModelConfigUi =
    ApiModelConfigUi(openRouterRouting = openRouterRouting)

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
        cachedPromptPrice = cachedPromptPrice,
        providerName = provider,
        isMultimodal = isMultimodal,
        isChatCapable = isChatCapable,
        isImageGenerationCapable = isImageGenerationCapable,
        isVideoGenerationCapable = isVideoGenerationCapable,
        isAudioGenerationCapable = isAudioGenerationCapable,
        tasks = tasks,
    )
}
