package com.browntowndev.enclave.domain.usecase.settings

import com.browntowndev.enclave.domain.model.config.ApiCredentialsId
import com.browntowndev.enclave.domain.model.config.ApiModelAsset
import com.browntowndev.enclave.domain.model.config.ApiModelConfiguration
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.config.ApiModelSavedPricing
import com.browntowndev.enclave.domain.model.config.LocalModelAsset
import com.browntowndev.enclave.domain.model.config.LocalModelConfiguration
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelId
import com.browntowndev.enclave.domain.model.config.MediaProviderId
import com.browntowndev.enclave.domain.model.config.ModelConfigurationId
import com.browntowndev.enclave.domain.model.config.OpenRouterRoutingConfiguration
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.ApiReasoningEffort
import com.browntowndev.enclave.domain.model.inference.DiscoveredApiModel
import com.browntowndev.enclave.domain.model.inference.ModelSource
import com.browntowndev.enclave.domain.model.inference.ModelType

data class ResolvedAssignedModelSelection(
    val localAsset: LocalModelAsset? = null,
    val localConfig: LocalModelConfiguration? = null,
    val apiAsset: ApiModelAsset? = null,
    val apiConfig: ApiModelConfiguration? = null,
    val ttsAsset: com.browntowndev.enclave.domain.model.config.TtsProviderAsset? = null,
    val liveVoiceAsset: com.browntowndev.enclave.domain.model.config.LiveVoiceProviderAsset? = null,
)

data class LocalModelPresetDraft(
    val id: LocalModelConfigurationId = LocalModelConfigurationId(""),
    val displayName: String = "",
    val maxTokens: String = "4096",
    val contextWindow: String = "4096",
    val temperature: Double = 0.7,
    val topP: Double = 0.95,
    val topK: String = "40",
    val minP: Double = 0.0,
    val repetitionPenalty: Double = 1.1,
    val thinkingEnabled: Boolean = false,
    val systemPrompt: String = "",
    val isSystemPreset: Boolean = false,
)

sealed interface ModelDeletionTarget {
    data class LocalModelAsset(
        val id: LocalModelId,
    ) : ModelDeletionTarget

    data class LocalModelPreset(
        val id: LocalModelConfigurationId,
    ) : ModelDeletionTarget

    data class ApiProvider(
        val id: ApiCredentialsId,
    ) : ModelDeletionTarget

    data class ApiPreset(
        val id: ApiModelConfigurationId,
    ) : ModelDeletionTarget

    data class ModelPack(
        val packId: String,
    ) : ModelDeletionTarget
}

data class ReassignmentCandidate(
    val configId: ModelConfigurationId? = null,
    val mediaProviderId: MediaProviderId? = null,
    val source: ModelSource,
    val assetDisplayName: String,
    val configDisplayName: String,
    val providerName: String? = null,
    val apiCredentialsId: ApiCredentialsId? = null,
    val localModelId: LocalModelId? = null,
) {
    init {
        require((configId != null) xor (mediaProviderId != null)) {
            "Reassignment candidate must specify either a model config or a media provider"
        }
    }
}

sealed interface PreparedModelDeletion {
    data object BlockedLastModel : PreparedModelDeletion

    data class Ready(
        val target: ModelDeletionTarget,
        val modelTypesNeedingReassignment: List<ModelType> = emptyList(),
        val reassignmentCandidates: List<ReassignmentCandidate> = emptyList(),
    ) : PreparedModelDeletion
}

data class ApiProviderDraft(
    val id: ApiCredentialsId = ApiCredentialsId(""),
    val displayName: String,
    val provider: ApiProvider,
    val baseUrl: String?,
    val credentialAlias: String,
    val apiKey: String,
    val sourceCredentialAlias: String? = null,
    val defaultReasoningEffort: ApiReasoningEffort? = null,
)

data class ApiProviderDraftSaveResult(
    val persistedAsset: ApiModelAsset,
    val createdPreset: ApiModelConfiguration? = null,
    val linkedExistingAssetDisplayName: String? = null,
)

data class ApiPresetDraft(
    val id: ApiModelConfigurationId = ApiModelConfigurationId(""),
    val credentialsId: ApiCredentialsId = ApiCredentialsId(""),
    val modelId: String = "",
    val isMultimodal: Boolean = false,
    val displayName: String = "",
    val maxTokens: String = "4096",
    val contextWindow: String = "4096",
    val temperature: Double = 0.7,
    val topP: Double = 0.95,
    val topK: String = "40",
    val minP: Double = 0.05,
    val frequencyPenalty: Double = 0.0,
    val presencePenalty: Double = 0.0,
    val maxTokensDisabled: Boolean = false,
    val temperatureDisabled: Boolean = false,
    val topPDisabled: Boolean = false,
    val topKDisabled: Boolean = false,
    val minPDisabled: Boolean = false,
    val frequencyPenaltyDisabled: Boolean = false,
    val presencePenaltyDisabled: Boolean = false,
    val systemPrompt: String = "",
    val reasoningEffort: ApiReasoningEffort? = null,
    val customHeaders: List<Pair<String, String>> = emptyList(),
    val openRouterRouting: OpenRouterRoutingConfiguration = OpenRouterRoutingConfiguration(),
    val pricing: ApiModelSavedPricing = ApiModelSavedPricing(),
)

data class ApiModelDiscoveryScope(
    val provider: ApiProvider,
    val baseUrl: String,
    val credentialAlias: String?,
)

data class ApiModelDiscoveryRequest(
    val provider: ApiProvider,
    val currentApiKey: String,
    val credentialAlias: String?,
    val baseUrl: String?,
    val selectedModelId: String?,
)

data class ApiModelDiscoveryResult(
    val models: List<DiscoveredApiModel>,
    val scope: ApiModelDiscoveryScope,
    val isTruncated: Boolean = false,
)

data class ApiModelMetadataDefaults(
    val reasoningEffort: ApiReasoningEffort?,
    val maxTokens: Int?,
    val contextWindow: Int?,
)

data class SearchSkillSettingsDraft(
    val enabled: Boolean = false,
    val tavilyKeyPresent: Boolean = false,
)

data class LiveVoiceProviderDraft(
    val id: com.browntowndev.enclave.domain.model.config.LiveVoiceProviderId =
        com.browntowndev.enclave.domain.model.config
            .LiveVoiceProviderId(""),
    val displayName: String,
    val provider: ApiProvider,
    val voiceName: String,
    val modelName: String? = null,
    val baseUrl: String? = null,
    val credentialAlias: String,
    val apiKey: String,
    val sourceCredentialAlias: String? = null,
)

data class TtsProviderDraft(
    val id: com.browntowndev.enclave.domain.model.config.TtsProviderId =
        com.browntowndev.enclave.domain.model.config
            .TtsProviderId(""),
    val displayName: String,
    val provider: ApiProvider,
    val voiceName: String,
    val modelName: String? = null,
    val baseUrl: String? = null,
    val credentialAlias: String,
    val apiKey: String,
    val sourceCredentialAlias: String? = null,
)

data class MediaProviderDraft(
    val id: com.browntowndev.enclave.domain.model.config.MediaProviderId =
        com.browntowndev.enclave.domain.model.config
            .MediaProviderId(""),
    val displayName: String,
    val provider: ApiProvider,
    val modelId: String,
    val capability: com.browntowndev.enclave.domain.model.config.MediaCapability,
    val baseUrl: String? = null,
    val credentialAlias: String,
    val apiKey: String,
    val sourceCredentialAlias: String? = null,
)
