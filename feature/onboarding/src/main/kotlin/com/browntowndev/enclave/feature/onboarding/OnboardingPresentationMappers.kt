package com.browntowndev.enclave.feature.onboarding

import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.config.MediaProviderId
import com.browntowndev.enclave.domain.model.config.MediaTask
import com.browntowndev.enclave.domain.model.config.TtsProviderId
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.DiscoveredApiModel
import com.browntowndev.enclave.domain.model.onboarding.OnboardingFlowMode
import com.browntowndev.enclave.domain.usecase.settings.MediaProviderDraft
import com.browntowndev.enclave.domain.usecase.settings.TtsProviderDraft

fun OnboardingFlowMode.toUi(): OnboardingFlowModeUi =
    when (this) {
        OnboardingFlowMode.FULL -> OnboardingFlowModeUi.FULL
        OnboardingFlowMode.RESTORED -> OnboardingFlowModeUi.RESTORED
    }

fun OnboardingFlowModeUi.toDomain(): OnboardingFlowMode =
    when (this) {
        OnboardingFlowModeUi.FULL -> OnboardingFlowMode.FULL
        OnboardingFlowModeUi.RESTORED -> OnboardingFlowMode.RESTORED
    }

fun ApiProvider.toUi(): ApiProviderUi = ApiProviderUi.valueOf(name)

fun ApiProviderUi.toDomain(): ApiProvider = ApiProvider.valueOf(name)

fun MediaCapability.toUi(): MediaTypeUi = MediaTypeUi.valueOf(name)

fun MediaTypeUi.toDomain(): MediaCapability = MediaCapability.valueOf(name)

fun DiscoveredApiModel.toUi(): DiscoveredApiModelUi =
    DiscoveredApiModelUi(
        id = id,
        name = name,
        contextWindowTokens = contextWindowTokens,
        maxOutputTokens = maxOutputTokens,
        created = created,
        promptPrice = promptPrice,
        completionPrice = completionPrice,
        cachedPromptPrice = cachedPromptPrice,
        isMultimodal = isMultimodal,
        isChatCapable = isChatCapable,
        isImageGenerationCapable = isImageGenerationCapable,
        isVideoGenerationCapable = isVideoGenerationCapable,
        isAudioGenerationCapable = isAudioGenerationCapable,
        tasks = tasks.map { it.name }.toSet(),
    )

fun DiscoveredApiModelUi.toDomain(): DiscoveredApiModel =
    DiscoveredApiModel(
        id = id,
        name = name,
        contextWindowTokens = contextWindowTokens,
        maxOutputTokens = maxOutputTokens,
        created = created,
        promptPrice = promptPrice,
        completionPrice = completionPrice,
        cachedPromptPrice = cachedPromptPrice,
        isMultimodal = isMultimodal,
        isChatCapable = isChatCapable,
        isImageGenerationCapable = isImageGenerationCapable,
        isVideoGenerationCapable = isVideoGenerationCapable,
        isAudioGenerationCapable = isAudioGenerationCapable,
        tasks = tasks.mapNotNull { runCatching { MediaTask.valueOf(it) }.getOrNull() }.toSet(),
    )

fun TtsProviderDraft.toUi(): TtsProviderDraftUi =
    TtsProviderDraftUi(
        id = id.value,
        displayName = displayName,
        provider = provider.toUi(),
        voiceName = voiceName,
        modelName = modelName,
        baseUrl = baseUrl,
        credentialAlias = credentialAlias,
        apiKey = apiKey,
        sourceCredentialAlias = sourceCredentialAlias,
    )

fun TtsProviderDraftUi.toDomain(): TtsProviderDraft =
    TtsProviderDraft(
        id = TtsProviderId(id),
        displayName = displayName,
        provider = provider.toDomain(),
        voiceName = voiceName,
        modelName = modelName,
        baseUrl = baseUrl,
        credentialAlias = credentialAlias,
        apiKey = apiKey,
        sourceCredentialAlias = sourceCredentialAlias,
    )

fun MediaProviderDraft.toUi(): MediaProviderDraftUi =
    MediaProviderDraftUi(
        id = id.value,
        displayName = displayName,
        provider = provider.toUi(),
        modelId = modelId,
        capability = capability.toUi(),
        baseUrl = baseUrl,
        credentialAlias = credentialAlias,
        apiKey = apiKey,
        sourceCredentialAlias = sourceCredentialAlias,
    )

fun MediaProviderDraftUi.toDomain(): MediaProviderDraft =
    MediaProviderDraft(
        id = MediaProviderId(id),
        displayName = displayName,
        provider = provider.toDomain(),
        modelId = modelId,
        capability = capability.toDomain(),
        baseUrl = baseUrl,
        credentialAlias = credentialAlias,
        apiKey = apiKey,
        sourceCredentialAlias = sourceCredentialAlias,
    )
