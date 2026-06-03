package com.browntowndev.enclave.feature.settings

import com.browntowndev.enclave.domain.model.config.ApiModelPricingFormat
import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.config.MediaTask
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.ApiProviderModelPolicy
import com.browntowndev.enclave.domain.model.inference.DiscoveredApiModel
import com.browntowndev.enclave.domain.usecase.settings.ApiModelDiscoveryScope

internal fun resolveApiCredentialAlias(
    state: ApiProvidersTransientState,
    asset: ApiModelAssetUi,
): String? =
    state.selectedReusableApiCredentialAlias?.takeIf { it.isNotBlank() }
        ?: asset.credentialAlias.takeIf { it.isNotBlank() }

internal fun resolveTtsCredentialAlias(
    state: TtsProvidersTransientState,
    asset: TtsProviderAssetUi,
): String? =
    state.selectedReusableApiCredentialAlias?.takeIf { it.isNotBlank() }
        ?: asset.credentialAlias.takeIf { it.isNotBlank() }

internal fun resolveMediaCredentialAlias(
    state: MediaProvidersTransientState,
    asset: MediaProviderAssetUi,
): String? =
    state.selectedReusableApiCredentialAlias?.takeIf { it.isNotBlank() }
        ?: asset.credentialAlias.takeIf { it.isNotBlank() }

internal fun ApiProvidersTransientState.discoveredModelFor(preset: ApiModelConfigUi?): DiscoveredApiModel? =
    discoveredApiModels.find { it.id == preset?.modelId }

internal fun ApiProvidersTransientState.normalizeVisionCapability(
    preset: ApiModelConfigUi?,
    models: List<DiscoveredApiModel> = discoveredApiModels,
): ApiModelConfigUi? = normalizePresetFromDiscovery(preset, models)

internal fun ApiProvidersTransientState.normalizePresetFromDiscovery(
    preset: ApiModelConfigUi?,
    models: List<DiscoveredApiModel> = discoveredApiModels,
): ApiModelConfigUi? {
    if (preset == null) return null
    val discovered = models.find { it.id == preset.modelId } ?: return preset
    return preset.copy(
        isMultimodal = discovered.isMultimodal ?: preset.isMultimodal,
        contextWindow = discovered.contextWindowTokens?.toString() ?: preset.contextWindow,
        promptPriceUsdPerMillion =
            discovered.promptPrice?.let(ApiModelPricingFormat::formatUsdPerMillion) ?: preset.promptPriceUsdPerMillion,
        completionPriceUsdPerMillion =
            discovered.completionPrice?.let(ApiModelPricingFormat::formatUsdPerMillion)
                ?: preset.completionPriceUsdPerMillion,
        cachedPromptPriceUsdPerMillion =
            discovered.cachedPromptPrice?.let(ApiModelPricingFormat::formatUsdPerMillion)
                ?: preset.cachedPromptPriceUsdPerMillion,
    )
}

internal fun ApiModelAssetUi?.matchesDiscoveryScope(other: ApiModelAssetUi?): Boolean {
    if (this == null || other == null) return false
    if (provider != other.provider) return false
    if (baseUrl.normalizedDiscoveryBaseUrl() != other.baseUrl.normalizedDiscoveryBaseUrl()) return false
    val currentAlias = credentialAlias.takeIf(String::isNotBlank)
    val nextAlias = other.credentialAlias.takeIf(String::isNotBlank)
    return currentAlias == null || nextAlias == null || currentAlias == nextAlias
}

internal fun String?.normalizedDiscoveryBaseUrl(): String = this?.trim().orEmpty()

internal fun List<DiscoveredApiModel>.mergeSelectedModel(preset: ApiModelConfigUi?): List<DiscoveredApiModel> =
    when {
        preset == null || preset.modelId.isBlank() -> this
        any { it.id == preset.modelId } -> this
        else -> listOf(DiscoveredApiModel(id = preset.modelId)) + this
    }

internal fun List<DiscoveredApiModel>.filterTtsModels(provider: ApiProvider): List<DiscoveredApiModel> =
    filter { model ->
        when (provider) {
            ApiProvider.GOOGLE -> model.id.isGoogleTtsModelCandidate()
            ApiProvider.FAL -> ApiProviderModelPolicy.isFalTtsModelCandidate(model.id)
            else -> model.id.isGoogleTtsModelCandidate() || model.isAudioGenerationCapable
        }
    }

internal fun List<DiscoveredApiModel>.filterChatModels(): List<DiscoveredApiModel> = filter { it.isChatCapable }

internal fun List<DiscoveredApiModel>.filterMediaModels(
    capability: MediaCapability,
    provider: ApiProvider,
    requiredTasks: Set<MediaTask> = emptySet(),
): List<DiscoveredApiModel> =
    filter { model ->
        val matchesCapability =
            when (capability) {
                MediaCapability.IMAGE -> model.isImageGenerationCapable
                MediaCapability.VIDEO -> model.isVideoGenerationCapable
                MediaCapability.MUSIC -> {
                    if (provider == ApiProvider.FAL) {
                        ApiProviderModelPolicy.isFalMusicModelCandidate(model.id)
                    } else {
                        model.isAudioGenerationCapable
                    }
                }
            }
        matchesCapability &&
            (requiredTasks.isEmpty() || model.tasks.any { it in requiredTasks })
    }

internal fun String.isGoogleTtsModelCandidate(): Boolean = endsWith("-tts") || endsWith("-tts-preview")

internal fun String.isFalTtsModelCandidate(): Boolean = ApiProviderModelPolicy.isFalTtsModelCandidate(this)

internal fun String.isFalMusicModelCandidate(): Boolean = ApiProviderModelPolicy.isFalMusicModelCandidate(this)

internal fun ApiModelAssetUi.toDiscoveryScope(): ApiModelDiscoveryScope =
    ApiModelDiscoveryScope(
        provider = provider,
        baseUrl = baseUrl.normalizedDiscoveryBaseUrl(),
        credentialAlias = credentialAlias.takeIf(String::isNotBlank),
    )

internal fun apiConnectionFingerprint(
    provider: com.browntowndev.enclave.domain.model.inference.ApiProvider,
    baseUrl: String?,
    apiKey: String,
    credentialAlias: String?,
): String = "${provider.name}|${baseUrl.normalizedDiscoveryBaseUrl()}|${apiKey.ifBlank { credentialAlias.orEmpty() }}"
