package com.browntowndev.enclave.feature.settings

import androidx.compose.runtime.Immutable
import com.browntowndev.enclave.domain.model.config.ApiModelConfiguration
import com.browntowndev.enclave.domain.model.config.OpenRouterDataCollectionPolicy
import com.browntowndev.enclave.domain.model.config.OpenRouterModelEndpoint
import com.browntowndev.enclave.domain.model.config.OpenRouterProviderSort
import com.browntowndev.enclave.domain.model.config.OpenRouterRoutingConfiguration
import com.browntowndev.enclave.domain.model.inference.ApiModelParameterSupport
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.ApiReasoningControlStyle
import com.browntowndev.enclave.domain.model.inference.ApiReasoningEffort
import com.browntowndev.enclave.domain.model.inference.ApiReasoningPolicy
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.profile.ProfileCostEstimate
import com.browntowndev.enclave.domain.model.settings.AppTheme

@Immutable
enum class AppThemeUi {
    SYSTEM,
    DYNAMIC,
    DARK,
    LIGHT,
}

internal fun AppTheme.toUi(): AppThemeUi =
    when (this) {
        AppTheme.SYSTEM -> AppThemeUi.SYSTEM
        AppTheme.DYNAMIC -> AppThemeUi.DYNAMIC
        AppTheme.DARK -> AppThemeUi.DARK
        AppTheme.LIGHT -> AppThemeUi.LIGHT
    }

internal fun AppThemeUi.toDomain(): AppTheme =
    when (this) {
        AppThemeUi.SYSTEM -> AppTheme.SYSTEM
        AppThemeUi.DYNAMIC -> AppTheme.DYNAMIC
        AppThemeUi.DARK -> AppTheme.DARK
        AppThemeUi.LIGHT -> AppTheme.LIGHT
    }

@Immutable
enum class ApiProviderUi(
    val displayName: String,
    val supportsChat: Boolean,
    val exposesDiscoveryModelPricing: Boolean,
    val defaultBaseUrl: String?,
    val defaultPresetTemperature: Double,
) {
    ANTHROPIC("Anthropic", true, false, null, 0.7),
    OPENAI("OpenAI", true, false, null, 0.7),
    OPENROUTER("OpenRouter", true, true, "https://openrouter.ai/api/v1", 0.7),
    XAI("xAI", true, true, "https://api.x.ai/v1", 0.7),
    GOOGLE("Google", true, false, "https://generativelanguage.googleapis.com", 0.7),
    FAL("fal.ai", false, false, "https://queue.fal.run", 0.7),
    ZAI("Z.ai", true, false, "https://api.z.ai/api/paas/v4", 1.0),
    DEEPSEEK("DeepSeek", true, false, "https://api.deepseek.com", 0.7),
    ;

    companion object {
        const val GENERIC_PRESET_TEMPERATURE: Double = 0.7
    }
}

internal fun ApiProvider.toUi(): ApiProviderUi =
    when (this) {
        ApiProvider.ANTHROPIC -> ApiProviderUi.ANTHROPIC
        ApiProvider.OPENAI -> ApiProviderUi.OPENAI
        ApiProvider.OPENROUTER -> ApiProviderUi.OPENROUTER
        ApiProvider.XAI -> ApiProviderUi.XAI
        ApiProvider.GOOGLE -> ApiProviderUi.GOOGLE
        ApiProvider.FAL -> ApiProviderUi.FAL
        ApiProvider.ZAI -> ApiProviderUi.ZAI
        ApiProvider.DEEPSEEK -> ApiProviderUi.DEEPSEEK
    }

internal fun ApiProviderUi.toDomain(): ApiProvider =
    when (this) {
        ApiProviderUi.ANTHROPIC -> ApiProvider.ANTHROPIC
        ApiProviderUi.OPENAI -> ApiProvider.OPENAI
        ApiProviderUi.OPENROUTER -> ApiProvider.OPENROUTER
        ApiProviderUi.XAI -> ApiProvider.XAI
        ApiProviderUi.GOOGLE -> ApiProvider.GOOGLE
        ApiProviderUi.FAL -> ApiProvider.FAL
        ApiProviderUi.ZAI -> ApiProvider.ZAI
        ApiProviderUi.DEEPSEEK -> ApiProvider.DEEPSEEK
    }

@Immutable
enum class ModelTypeUi(
    val displayLabel: String,
) {
    MAIN("Synthesis"),
    FAST("Fast"),
    THINKING("Thinking"),
    VISION("Vision (API)"),
    TTS("Text-to-Speech"),
    LIVE_VOICE("Live Voice"),
    IMAGE_GENERATION("Image Generation"),
    IMAGE_EDIT("Image Edit"),
    VIDEO_GENERATION("Video Generation"),
    IMAGE_TO_VIDEO("Image to Video"),
    MUSIC_GENERATION("Music Generation"),
    DRAFT_ONE("Draft 1"),
    DRAFT_TWO("Draft 2"),
    FINAL_SYNTHESIS("Final Refinement"),
    UNASSIGNED("Unassigned"),
}

internal fun ModelType.toUi(): ModelTypeUi =
    when (this) {
        ModelType.MAIN -> ModelTypeUi.MAIN
        ModelType.FAST -> ModelTypeUi.FAST
        ModelType.THINKING -> ModelTypeUi.THINKING
        ModelType.VISION -> ModelTypeUi.VISION
        ModelType.TTS -> ModelTypeUi.TTS
        ModelType.LIVE_VOICE -> ModelTypeUi.LIVE_VOICE
        ModelType.IMAGE_GENERATION -> ModelTypeUi.IMAGE_GENERATION
        ModelType.IMAGE_EDIT -> ModelTypeUi.IMAGE_EDIT
        ModelType.VIDEO_GENERATION -> ModelTypeUi.VIDEO_GENERATION
        ModelType.IMAGE_TO_VIDEO -> ModelTypeUi.IMAGE_TO_VIDEO
        ModelType.MUSIC_GENERATION -> ModelTypeUi.MUSIC_GENERATION
        ModelType.DRAFT_ONE -> ModelTypeUi.DRAFT_ONE
        ModelType.DRAFT_TWO -> ModelTypeUi.DRAFT_TWO
        ModelType.FINAL_SYNTHESIS -> ModelTypeUi.FINAL_SYNTHESIS
        ModelType.UNASSIGNED -> ModelTypeUi.UNASSIGNED
    }

internal fun ModelTypeUi.toDomain(): ModelType =
    when (this) {
        ModelTypeUi.MAIN -> ModelType.MAIN
        ModelTypeUi.FAST -> ModelType.FAST
        ModelTypeUi.THINKING -> ModelType.THINKING
        ModelTypeUi.VISION -> ModelType.VISION
        ModelTypeUi.TTS -> ModelType.TTS
        ModelTypeUi.LIVE_VOICE -> ModelType.LIVE_VOICE
        ModelTypeUi.IMAGE_GENERATION -> ModelType.IMAGE_GENERATION
        ModelTypeUi.IMAGE_EDIT -> ModelType.IMAGE_EDIT
        ModelTypeUi.VIDEO_GENERATION -> ModelType.VIDEO_GENERATION
        ModelTypeUi.IMAGE_TO_VIDEO -> ModelType.IMAGE_TO_VIDEO
        ModelTypeUi.MUSIC_GENERATION -> ModelType.MUSIC_GENERATION
        ModelTypeUi.DRAFT_ONE -> ModelType.DRAFT_ONE
        ModelTypeUi.DRAFT_TWO -> ModelType.DRAFT_TWO
        ModelTypeUi.FINAL_SYNTHESIS -> ModelType.FINAL_SYNTHESIS
        ModelTypeUi.UNASSIGNED -> ModelType.UNASSIGNED
    }

@Immutable
enum class ApiReasoningEffortUi(
    val displayName: String,
) {
    NONE("None"),
    LOW("Low"),
    MEDIUM("Medium"),
    HIGH("High"),
    XHIGH("X-High"),
}

internal fun ApiReasoningEffort.toUi(): ApiReasoningEffortUi =
    when (this) {
        ApiReasoningEffort.NONE -> ApiReasoningEffortUi.NONE
        ApiReasoningEffort.LOW -> ApiReasoningEffortUi.LOW
        ApiReasoningEffort.MEDIUM -> ApiReasoningEffortUi.MEDIUM
        ApiReasoningEffort.HIGH -> ApiReasoningEffortUi.HIGH
        ApiReasoningEffort.XHIGH -> ApiReasoningEffortUi.XHIGH
    }

internal fun ApiReasoningEffortUi.toDomain(): ApiReasoningEffort =
    when (this) {
        ApiReasoningEffortUi.NONE -> ApiReasoningEffort.NONE
        ApiReasoningEffortUi.LOW -> ApiReasoningEffort.LOW
        ApiReasoningEffortUi.MEDIUM -> ApiReasoningEffort.MEDIUM
        ApiReasoningEffortUi.HIGH -> ApiReasoningEffort.HIGH
        ApiReasoningEffortUi.XHIGH -> ApiReasoningEffort.XHIGH
    }

@Immutable
enum class ApiReasoningControlStyleUi {
    STANDARD,
    XAI_MULTI_AGENT,
}

@Immutable
data class ApiReasoningPolicyUi(
    val controlStyle: ApiReasoningControlStyleUi,
    val supportedEfforts: List<ApiReasoningEffortUi>,
    val defaultEffort: ApiReasoningEffortUi,
)

@Immutable
data class ApiModelParameterSupportUi(
    val reasoningPolicy: ApiReasoningPolicyUi,
    val supportsMaxTokens: Boolean = true,
    val supportsTemperature: Boolean = true,
    val supportsTopP: Boolean = true,
    val supportsTopK: Boolean = true,
    val supportsMinP: Boolean = true,
    val supportsFrequencyPenalty: Boolean = true,
    val supportsPresencePenalty: Boolean = true,
    val supportsReasoningEffort: Boolean = true,
) {
    companion object {
        val DEFAULT =
            ApiModelParameterSupportUi(
                reasoningPolicy =
                    ApiReasoningPolicyUi(
                        controlStyle = ApiReasoningControlStyleUi.STANDARD,
                        supportedEfforts =
                            listOf(
                                ApiReasoningEffortUi.LOW,
                                ApiReasoningEffortUi.MEDIUM,
                                ApiReasoningEffortUi.HIGH,
                                ApiReasoningEffortUi.XHIGH,
                            ),
                        defaultEffort = ApiReasoningEffortUi.LOW,
                    ),
            )
    }
}

internal fun ApiReasoningPolicy.toUi(): ApiReasoningPolicyUi =
    ApiReasoningPolicyUi(
        controlStyle =
            when (controlStyle) {
                ApiReasoningControlStyle.STANDARD -> ApiReasoningControlStyleUi.STANDARD
                ApiReasoningControlStyle.XAI_MULTI_AGENT -> ApiReasoningControlStyleUi.XAI_MULTI_AGENT
            },
        supportedEfforts = supportedEfforts.map { it.toUi() },
        defaultEffort = defaultEffort.toUi(),
    )

internal fun ApiModelParameterSupport.toUi(): ApiModelParameterSupportUi =
    ApiModelParameterSupportUi(
        reasoningPolicy = reasoningPolicy.toUi(),
        supportsMaxTokens = supportsMaxTokens,
        supportsTemperature = supportsTemperature,
        supportsTopP = supportsTopP,
        supportsTopK = supportsTopK,
        supportsMinP = supportsMinP,
        supportsFrequencyPenalty = supportsFrequencyPenalty,
        supportsPresencePenalty = supportsPresencePenalty,
        supportsReasoningEffort = supportsReasoningEffort,
    )

@Immutable
enum class OpenRouterProviderSortUi(
    val displayName: String,
) {
    PRICE("Price"),
    LATENCY("Latency"),
    THROUGHPUT("Throughput"),
}

@Immutable
enum class OpenRouterDataCollectionPolicyUi {
    ALLOW,
    DENY,
}

@Immutable
data class OpenRouterRoutingUi(
    val providerSort: OpenRouterProviderSortUi = OpenRouterProviderSortUi.THROUGHPUT,
    val allowFallbacks: Boolean = true,
    val requireParameters: Boolean = false,
    val dataCollectionPolicy: OpenRouterDataCollectionPolicyUi = OpenRouterDataCollectionPolicyUi.DENY,
    val zeroDataRetention: Boolean = false,
    val preferredProvider: String? = null,
)

internal fun OpenRouterRoutingConfiguration.toUi(): OpenRouterRoutingUi =
    OpenRouterRoutingUi(
        providerSort =
            when (providerSort) {
                OpenRouterProviderSort.PRICE -> OpenRouterProviderSortUi.PRICE
                OpenRouterProviderSort.LATENCY -> OpenRouterProviderSortUi.LATENCY
                OpenRouterProviderSort.THROUGHPUT -> OpenRouterProviderSortUi.THROUGHPUT
            },
        allowFallbacks = allowFallbacks,
        requireParameters = requireParameters,
        dataCollectionPolicy =
            when (dataCollectionPolicy) {
                OpenRouterDataCollectionPolicy.ALLOW -> OpenRouterDataCollectionPolicyUi.ALLOW
                OpenRouterDataCollectionPolicy.DENY -> OpenRouterDataCollectionPolicyUi.DENY
            },
        zeroDataRetention = zeroDataRetention,
        preferredProvider = preferredProvider,
    )

internal fun OpenRouterRoutingUi.toDomain(): OpenRouterRoutingConfiguration =
    OpenRouterRoutingConfiguration(
        providerSort =
            when (providerSort) {
                OpenRouterProviderSortUi.PRICE -> OpenRouterProviderSort.PRICE
                OpenRouterProviderSortUi.LATENCY -> OpenRouterProviderSort.LATENCY
                OpenRouterProviderSortUi.THROUGHPUT -> OpenRouterProviderSort.THROUGHPUT
            },
        allowFallbacks = allowFallbacks,
        requireParameters = requireParameters,
        dataCollectionPolicy =
            when (dataCollectionPolicy) {
                OpenRouterDataCollectionPolicyUi.ALLOW -> OpenRouterDataCollectionPolicy.ALLOW
                OpenRouterDataCollectionPolicyUi.DENY -> OpenRouterDataCollectionPolicy.DENY
            },
        zeroDataRetention = zeroDataRetention,
        preferredProvider = preferredProvider,
    )

@Immutable
data class OpenRouterModelEndpointUi(
    val providerName: String,
    val providerSlug: String,
    val contextLength: Int,
    val promptPriceUsdPerMillion: Double,
    val completionPriceUsdPerMillion: Double,
    val quantization: String,
    val isOperational: Boolean,
    val uptime1d: Double?,
    val latency30m: Double?,
    val throughput30m: Double?,
)

internal fun OpenRouterModelEndpoint.toUi(): OpenRouterModelEndpointUi =
    OpenRouterModelEndpointUi(
        providerName = providerName,
        providerSlug = providerSlug,
        contextLength = contextLength,
        promptPriceUsdPerMillion = promptPriceUsdPerMillion,
        completionPriceUsdPerMillion = completionPriceUsdPerMillion,
        quantization = quantization,
        isOperational = isOperational,
        uptime1d = uptime1d,
        latency30m = latency30m,
        throughput30m = throughput30m,
    )

@Immutable
data class ConfiguredModelUi(
    val id: String,
    val displayName: String,
    val modelId: String,
    val isMultimodal: Boolean,
)

internal fun ApiModelConfiguration.toUi(): ConfiguredModelUi =
    ConfiguredModelUi(
        id = id.value,
        displayName = displayName,
        modelId = modelId,
        isMultimodal = isMultimodal,
    )

@Immutable
data class ProfileCostEstimateUi(
    val estimatedInputTokens: Int,
    val estimatedOutputTokens: Int,
    val estimatedCostUsd: String?,
    val selectedConversationCount: Int,
    val totalConversationCount: Int,
)

internal fun ProfileCostEstimate.toUi(): ProfileCostEstimateUi =
    ProfileCostEstimateUi(
        estimatedInputTokens = estimatedInputTokens,
        estimatedOutputTokens = estimatedOutputTokens,
        estimatedCostUsd = estimatedCostUsd?.toString(),
        selectedConversationCount = selectedConversationCount,
        totalConversationCount = totalConversationCount,
    )

@Immutable
sealed interface ReDownloadProgressUi {
    data object Idle : ReDownloadProgressUi

    data object Preparing : ReDownloadProgressUi

    data class Downloading(
        val progress: Float,
    ) : ReDownloadProgressUi

    data class Finalizing(
        val progress: Float?,
    ) : ReDownloadProgressUi

    data object Complete : ReDownloadProgressUi

    data class Failed(
        val error: String,
    ) : ReDownloadProgressUi
}
