package com.browntowndev.enclave.feature.settings

import com.browntowndev.enclave.domain.model.config.MediaCapability
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
): ApiModelConfigUi? {
    if (preset == null) return null
    val discoveredVisionCapability =
        models
            .find { it.id == preset.modelId }
            ?.isMultimodal
    return discoveredVisionCapability?.let { preset.copy(isMultimodal = it) } ?: preset
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

internal fun apiConnectionFingerprint(
    provider: com.browntowndev.enclave.domain.model.inference.ApiProvider,
    baseUrl: String?,
    apiKey: String,
    credentialAlias: String?,
): String =
    "${provider.name}|${baseUrl.normalizedDiscoveryBaseUrl()}|${apiKey.ifBlank { credentialAlias.orEmpty() }}"
