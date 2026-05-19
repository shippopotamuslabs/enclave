package com.browntowndev.pocketcrew.feature.settings

import com.browntowndev.pocketcrew.domain.model.config.MediaCapability
import com.browntowndev.pocketcrew.domain.model.inference.DiscoveredApiModel
import com.browntowndev.pocketcrew.domain.usecase.settings.ApiModelDiscoveryScope

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

internal fun ApiProvidersTransientState.discoveredModelFor(asset: ApiModelAssetUi?): DiscoveredApiModel? =
    discoveredApiModels.find { it.id == asset?.modelId }

internal fun ApiProvidersTransientState.normalizeVisionCapability(
    asset: ApiModelAssetUi?,
    models: List<DiscoveredApiModel> = discoveredApiModels,
): ApiModelAssetUi? {
    if (asset == null) return null
    val discoveredVisionCapability =
        models
            .find { it.id == asset.modelId }
            ?.isMultimodal
    return discoveredVisionCapability?.let { asset.copy(isMultimodal = it) } ?: asset
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

internal fun List<DiscoveredApiModel>.mergeSelectedModel(asset: ApiModelAssetUi?): List<DiscoveredApiModel> =
    when {
        asset == null || asset.modelId.isBlank() -> this
        any { it.id == asset.modelId } -> this
        else -> listOf(DiscoveredApiModel(id = asset.modelId)) + this
    }

internal fun List<DiscoveredApiModel>.filterGoogleTtsModels(): List<DiscoveredApiModel> =
    filter { it.id.isGoogleTtsModelCandidate() }

internal fun List<DiscoveredApiModel>.filterChatModels(): List<DiscoveredApiModel> = filter { it.isChatCapable }

internal fun List<DiscoveredApiModel>.filterMediaModels(capability: MediaCapability): List<DiscoveredApiModel> =
    filter { model ->
        when (capability) {
            MediaCapability.IMAGE -> model.isImageGenerationCapable
            MediaCapability.VIDEO -> model.isVideoGenerationCapable
            MediaCapability.MUSIC -> model.isAudioGenerationCapable
        }
    }

internal fun String.isGoogleTtsModelCandidate(): Boolean = endsWith("-tts") || endsWith("-tts-preview")

internal fun ApiModelAssetUi.toDiscoveryScope(): ApiModelDiscoveryScope =
    ApiModelDiscoveryScope(
        provider = provider,
        baseUrl = baseUrl.normalizedDiscoveryBaseUrl(),
        credentialAlias = credentialAlias.takeIf(String::isNotBlank),
    )
