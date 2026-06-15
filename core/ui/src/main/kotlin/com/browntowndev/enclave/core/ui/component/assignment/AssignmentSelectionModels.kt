package com.browntowndev.enclave.core.ui.component.assignment

import androidx.compose.runtime.Immutable
import com.browntowndev.enclave.domain.model.config.ApiCredentialsId
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LiveVoiceProviderId
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelId
import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.config.MediaProviderId
import com.browntowndev.enclave.domain.model.config.MediaTask
import com.browntowndev.enclave.domain.model.config.OpenRouterRoutingConfiguration
import com.browntowndev.enclave.domain.model.config.TtsProviderId
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.ApiReasoningEffort
import com.browntowndev.enclave.domain.model.inference.ModelType

@Immutable
data class TtsProviderAssetUi(
    val id: TtsProviderId = TtsProviderId(""),
    val displayName: String = "",
    val provider: ApiProvider = ApiProvider.OPENAI,
    val voiceName: String = "",
    val modelName: String? = null,
    val baseUrl: String? = null,
    val credentialAlias: String = "",
    val useAsDefault: Boolean = false,
)

@Immutable
data class LiveVoiceProviderAssetUi(
    val id: LiveVoiceProviderId = LiveVoiceProviderId(""),
    val displayName: String = "",
    val provider: ApiProvider = ApiProvider.OPENAI,
    val voiceName: String = "",
    val modelName: String? = null,
    val baseUrl: String? = null,
    val credentialAlias: String = "",
    val useAsDefault: Boolean = false,
)

@Immutable
data class MediaProviderAssetUi(
    val id: MediaProviderId = MediaProviderId(""),
    val displayName: String = "",
    val provider: ApiProvider = ApiProvider.OPENAI,
    val capability: MediaCapability = MediaCapability.IMAGE,
    val tasks: Set<MediaTask> = emptySet(),
    val modelName: String = "",
    val baseUrl: String? = null,
    val credentialAlias: String = "",
    val useAsDefault: Boolean = false,
)

@Immutable
data class ApiModelAssetUi(
    val credentialsId: ApiCredentialsId,
    val displayName: String,
    val provider: ApiProvider,
    val baseUrl: String?,
    val credentialAlias: String,
    val configurations: List<ApiModelConfigUi>,
)

@Immutable
data class ReusableApiCredentialUi(
    val credentialsId: ApiCredentialsId,
    val displayName: String,
    val providerLabel: String,
    val credentialAlias: String,
)

@Immutable
data class CustomHeaderUi(
    val key: String = "",
    val value: String = "",
)

@Immutable
data class ApiModelConfigUi(
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
    val customHeaders: List<CustomHeaderUi> = emptyList(),
    val openRouterRouting: OpenRouterRoutingConfiguration = OpenRouterRoutingConfiguration(),
    val thinkingEnabled: Boolean = false,
    val systemPrompt: String = "",
    val reasoningEffort: ApiReasoningEffort? = null,
    val promptPriceUsdPerMillion: String = "",
    val completionPriceUsdPerMillion: String = "",
    val cachedPromptPriceUsdPerMillion: String = "",
)

@Immutable
data class LocalModelAssetUi(
    val metadataId: LocalModelId,
    val huggingFaceModelName: String,
    val friendlyName: String,
    val providerName: String,
    val format: String,
    val remoteFileName: String,
    val sizeInBytes: Long,
    val configurations: List<LocalModelConfigUi>,
    val isExpanded: Boolean = false,
    val isMultimodal: Boolean = false,
    val isImageGenerationCapable: Boolean = false,
    val isSoftDeleted: Boolean = false,
)

@Immutable
data class LocalModelConfigUi(
    val id: LocalModelConfigurationId = LocalModelConfigurationId(""),
    val localModelId: LocalModelId = LocalModelId(""),
    val displayName: String = "",
    val maxTokens: String = "4096",
    val contextWindow: String = "4096",
    val temperature: Double = 0.7,
    val topP: Double = 0.95,
    val topK: String = "40",
    val minP: Double = 0.0,
    val repetitionPenalty: Double = 1.1,
    val systemPrompt: String = "",
    val thinkingEnabled: Boolean = false,
    val reasoningBudget: String = "1024",
    val isSystemPreset: Boolean = false,
    val defaultAssignments: List<ModelType> = emptyList(),
)
