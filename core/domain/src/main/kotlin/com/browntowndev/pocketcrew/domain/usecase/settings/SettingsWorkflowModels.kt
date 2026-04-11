package com.browntowndev.pocketcrew.domain.usecase.settings

import com.browntowndev.pocketcrew.domain.model.config.ApiCredentialsId
import com.browntowndev.pocketcrew.domain.model.config.ApiModelAsset
import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelId
import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.ModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.OpenRouterRoutingConfiguration
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import com.browntowndev.pocketcrew.domain.model.inference.ApiReasoningEffort
import com.browntowndev.pocketcrew.domain.model.inference.DiscoveredApiModel
import com.browntowndev.pocketcrew.domain.model.inference.ModelSource
import com.browntowndev.pocketcrew.domain.model.inference.ModelType

data class ResolvedAssignedModelSelection(
    val localAsset: LocalModelAsset? = null,
    val localConfig: LocalModelConfiguration? = null,
    val apiAsset: ApiModelAsset? = null,
    val apiConfig: ApiModelConfiguration? = null,
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
    data class LocalModelAsset(val id: LocalModelId) : ModelDeletionTarget
    data class LocalModelPreset(val id: LocalModelConfigurationId) : ModelDeletionTarget
    data class ApiProvider(val id: ApiCredentialsId) : ModelDeletionTarget
    data class ApiPreset(val id: ApiModelConfigurationId) : ModelDeletionTarget
}

data class ReassignmentCandidate(
    val configId: ModelConfigurationId,
    val source: ModelSource,
    val assetDisplayName: String,
    val configDisplayName: String,
    val providerName: String? = null,
    val apiCredentialsId: ApiCredentialsId? = null,
    val localModelId: LocalModelId? = null,
)

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
    val modelId: String,
    val baseUrl: String?,
    val isVision: Boolean,
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
    val displayName: String = "",
    val maxTokens: String = "4096",
    val contextWindow: String = "4096",
    val temperature: Double = 0.7,
    val topP: Double = 0.95,
    val topK: String = "40",
    val minP: Double = 0.05,
    val frequencyPenalty: Double = 0.0,
    val presencePenalty: Double = 0.0,
    val systemPrompt: String = "",
    val reasoningEffort: ApiReasoningEffort? = null,
    val customHeaders: List<Pair<String, String>> = emptyList(),
    val openRouterRouting: OpenRouterRoutingConfiguration = OpenRouterRoutingConfiguration(),
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
