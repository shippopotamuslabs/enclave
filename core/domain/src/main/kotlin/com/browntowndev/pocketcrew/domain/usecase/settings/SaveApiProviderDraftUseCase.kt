package com.browntowndev.pocketcrew.domain.usecase.settings

import com.browntowndev.pocketcrew.domain.model.config.ApiCredentials
import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfiguration
import com.browntowndev.pocketcrew.domain.usecase.byok.GetApiModelAssetsUseCase
import com.browntowndev.pocketcrew.domain.usecase.byok.SaveApiCredentialsUseCase
import com.browntowndev.pocketcrew.domain.usecase.byok.SaveApiModelConfigurationUseCase
import com.browntowndev.pocketcrew.domain.port.repository.ApiModelRepositoryPort
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class SaveApiProviderDraftUseCase @Inject constructor(
    private val saveApiCredentialsUseCase: SaveApiCredentialsUseCase,
    private val saveApiModelConfigurationUseCase: SaveApiModelConfigurationUseCase,
    private val getApiModelAssetsUseCase: GetApiModelAssetsUseCase,
    private val apiModelRepository: ApiModelRepositoryPort,
) {
    suspend operator fun invoke(draft: ApiProviderDraft): Result<ApiProviderDraftSaveResult> {
        return Result.runCatching {
            val existingAssets = getApiModelAssetsUseCase().first()
            val isNewAsset = draft.id == 0L
            val linkedExistingAsset = if (isNewAsset) {
                apiModelRepository.findMatchingCredentials(
                    provider = draft.provider,
                    modelId = draft.modelId,
                    baseUrl = draft.baseUrl,
                    apiKey = draft.apiKey,
                    sourceCredentialAlias = draft.sourceCredentialAlias,
                )?.let { matchedCredentials ->
                    existingAssets.firstOrNull { it.credentials.id == matchedCredentials.id }
                        ?: getApiModelAssetsUseCase()
                            .first { assets -> assets.any { it.credentials.id == matchedCredentials.id } }
                            .first { it.credentials.id == matchedCredentials.id }
                }
            } else {
                null
            }
            val finalAlias = if (isNewAsset && linkedExistingAsset == null) {
                generateUniqueAlias(
                    provider = draft.provider.name,
                    modelId = draft.modelId,
                    existingAliases = existingAssets.map { it.credentials.credentialAlias }.toSet(),
                )
            } else {
                draft.credentialAlias
            }

            val savedId = linkedExistingAsset?.credentials?.id ?: saveApiCredentialsUseCase(
                credentials = ApiCredentials(
                    id = draft.id,
                    displayName = draft.displayName,
                    provider = draft.provider,
                    modelId = draft.modelId,
                    baseUrl = draft.baseUrl.takeIf { !it.isNullOrBlank() } ?: draft.provider.defaultBaseUrl(),
                    isVision = draft.isVision,
                    credentialAlias = finalAlias,
                ),
                apiKey = draft.apiKey,
                sourceCredentialAlias = draft.sourceCredentialAlias,
            )

            var persistedAsset = linkedExistingAsset
                ?: getApiModelAssetsUseCase()
                    .first { assets -> assets.any { it.credentials.id == savedId } }
                    .first { it.credentials.id == savedId }

            val createdPreset = if (isNewAsset) {
                val configId = saveApiModelConfigurationUseCase(
                    ApiModelConfiguration(
                        apiCredentialsId = savedId,
                        displayName = generateUniquePresetName(persistedAsset.configurations.map(ApiModelConfiguration::displayName)),
                        reasoningEffort = draft.defaultReasoningEffort,
                    )
                ).getOrThrow()
                persistedAsset = getApiModelAssetsUseCase()
                    .first { assets ->
                        assets.any { asset ->
                            asset.credentials.id == savedId && asset.configurations.any { it.id == configId }
                        }
                    }
                    .first { it.credentials.id == savedId }
                persistedAsset.configurations.firstOrNull { it.id == configId }
            } else {
                null
            }

            ApiProviderDraftSaveResult(
                persistedAsset = persistedAsset,
                createdPreset = createdPreset,
                linkedExistingAssetDisplayName = linkedExistingAsset?.credentials?.displayName,
            )
        }
    }

    private fun generateUniqueAlias(
        provider: String,
        modelId: String,
        existingAliases: Set<String>,
    ): String {
        val baseSlug = "${provider.lowercase()}-${modelId.lowercase()}"
            .replace(Regex("[^a-z0-9]"), "-")
            .replace(Regex("-+"), "-")
            .trim('-')

        if (baseSlug !in existingAliases) {
            return baseSlug
        }

        var counter = 2
        while ("$baseSlug-$counter" in existingAliases) {
            counter++
        }
        return "$baseSlug-$counter"
    }

    private fun generateUniquePresetName(existingNames: List<String>): String {
        val names = existingNames.toSet()
        val baseName = "Default Preset"
        if (baseName !in names) {
            return baseName
        }

        var counter = 2
        while ("$baseName $counter" in names) {
            counter++
        }
        return "$baseName $counter"
    }
}
