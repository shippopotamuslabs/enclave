package com.browntowndev.enclave.feature.settings

import com.browntowndev.enclave.core.ui.component.assignment.ApiModelAssetUi
import com.browntowndev.enclave.core.ui.component.assignment.ApiModelConfigUi
import com.browntowndev.enclave.domain.model.config.ApiCredentialsId
import com.browntowndev.enclave.domain.model.config.DefaultModelAssignment
import com.browntowndev.enclave.domain.model.config.OpenRouterRoutingConfiguration
import com.browntowndev.enclave.domain.model.error.ActiveModelConfigurationDecryptException
import com.browntowndev.enclave.domain.model.inference.DiscoveredApiModel
import com.browntowndev.enclave.domain.model.inference.ModelSource
import com.browntowndev.enclave.domain.usecase.settings.ReassignmentCandidate
import javax.inject.Inject

typealias ApiModelAssetUiMapper = com.browntowndev.enclave.core.ui.component.assignment.ApiModelAssetUiMapper

typealias LocalModelAssetUiMapper = com.browntowndev.enclave.core.ui.component.assignment.LocalModelAssetUiMapper

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
                    mediaProviderId = candidate.mediaProviderId,
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
                liveVoiceProviderId != null -> ModelSource.API
                else -> ModelSource.API
            },
        currentModelName =
            displayName
                ?: when {
                    localConfigId != null ||
                        apiConfigId != null ||
                        ttsProviderId != null ||
                        mediaProviderId != null ||
                        liveVoiceProviderId != null ->
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
                        ttsProviderId != null ||
                        liveVoiceProviderId != null ->
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
