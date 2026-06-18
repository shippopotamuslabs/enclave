package com.browntowndev.enclave.core.ui.component.assignment

import com.browntowndev.enclave.domain.model.config.ApiModelAsset
import com.browntowndev.enclave.domain.model.config.ApiModelConfiguration
import com.browntowndev.enclave.domain.model.config.ApiModelPricingFormat
import com.browntowndev.enclave.domain.model.config.LocalModelAsset
import com.browntowndev.enclave.domain.model.config.LocalModelConfiguration
import com.browntowndev.enclave.domain.model.inference.ModelFileFormat
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationModelRegistry
import com.browntowndev.enclave.domain.model.media.LocalStableDiffusionModelPolicy
import com.browntowndev.enclave.domain.port.media.LOCAL_ON_DEVICE_IMAGE_GENERATION_ENABLED
import javax.inject.Inject

private object LocalModelAssetConstants {
    val dashGgufRegex = Regex("-GGUF$", RegexOption.IGNORE_CASE)
    val dotGgufRegex = Regex("\\.gguf$", RegexOption.IGNORE_CASE)
    val dotLitertlmRegex = Regex("\\.litertlm$", RegexOption.IGNORE_CASE)
    val dotBinRegex = Regex("\\.bin$", RegexOption.IGNORE_CASE)
    val dotOnnxRegex = Regex("\\.onnx$", RegexOption.IGNORE_CASE)
    val dotTxtRegex = Regex("\\.txt$", RegexOption.IGNORE_CASE)
}

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
                includeReasoningContentOnRequest = config.includeReasoningContentOnRequest,
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
                    ModelFileFormat.SAFETENSORS -> "SAFETENSORS"
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

            val isImageGenerationCapable =
                LOCAL_ON_DEVICE_IMAGE_GENERATION_ENABLED &&
                    LocalStableDiffusionModelPolicy.supports(asset)

            // The persisted local registry drops each config's defaultAssignments, so derive the
            // image-gen slot membership from the descriptor; otherwise SD/SDXL/Juggernaut never
            // surface as assignable options for the Image Generation / Image Edit roles.
            val imageGenSlots =
                if (isImageGenerationCapable) {
                    LocalImageGenerationModelRegistry.assignableSlotsFor(asset)
                } else {
                    emptyList()
                }
            val configurations =
                asset.configurations.map { config ->
                    val mapped = mapConfig(config)
                    if (mapped.defaultAssignments.isEmpty() && imageGenSlots.isNotEmpty()) {
                        mapped.copy(defaultAssignments = imageGenSlots)
                    } else {
                        mapped
                    }
                }

            return LocalModelAssetUi(
                metadataId = asset.metadata.id,
                huggingFaceModelName = rawName,
                friendlyName = friendlyName,
                providerName = providerName,
                format = format,
                remoteFileName = asset.metadata.remoteFileName,
                sizeInBytes = asset.metadata.sizeInBytes,
                configurations = configurations,
                isMultimodal = asset.metadata.isMultimodal,
                isImageGenerationCapable = isImageGenerationCapable,
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
                reasoningBudget = config.reasoningBudget.toString(),
                isSystemPreset = config.isSystemPreset,
                defaultAssignments = config.defaultAssignments,
            )
    }
