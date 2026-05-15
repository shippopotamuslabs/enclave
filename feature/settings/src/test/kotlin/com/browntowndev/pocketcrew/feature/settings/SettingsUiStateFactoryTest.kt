package com.browntowndev.pocketcrew.feature.settings

import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelMetadata
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat
import com.browntowndev.pocketcrew.domain.port.auth.AuthState
import com.browntowndev.pocketcrew.domain.port.repository.SettingsData
import com.browntowndev.pocketcrew.domain.usecase.settings.ApplyApiModelMetadataDefaultsUseCase
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SettingsUiStateFactoryTest {

    private lateinit var factory: SettingsUiStateFactory

    @BeforeEach
    fun setup() {
        factory = SettingsUiStateFactory(
            localModelAssetUiMapper = LocalModelAssetUiMapper(),
            apiModelAssetUiMapper = ApiModelAssetUiMapper(),
            ttsProviderAssetUiMapper = TtsProviderAssetUiMapper(),
            mediaProviderAssetUiMapper = mockk(relaxed = true),
            apiDiscoveryUiFilter = ApiDiscoveryUiFilter(),
            applyApiModelMetadataDefaultsUseCase = ApplyApiModelMetadataDefaultsUseCase(),
        )
    }

    // region availableDownloads filtering

    @Test
    fun `availableDownloads contains only soft-deleted models`() {
        val downloadedModel = localAsset(id = "downloaded", withConfig = true)
        val softDeletedModel = localAsset(id = "soft-deleted", withConfig = false)

        val state = factory.create(
            localAssets = listOf(downloadedModel, softDeletedModel),
            defaults = defaultFactoryArgs()
        )

        val downloads = state.localModelsSheet.availableDownloads
        assertEquals(1, downloads.size)
        assertEquals(LocalModelId("soft-deleted"), downloads[0].metadataId)
        assertTrue(downloads[0].isSoftDeleted)
    }

    @Test
    fun `models contains only non-soft-deleted models`() {
        val downloadedModel = localAsset(id = "downloaded", withConfig = true)
        val softDeletedModel = localAsset(id = "soft-deleted", withConfig = false)

        val state = factory.create(
            localAssets = listOf(downloadedModel, softDeletedModel),
            defaults = defaultFactoryArgs()
        )

        val models = state.localModelsSheet.models
        assertEquals(1, models.size)
        assertEquals(LocalModelId("downloaded"), models[0].metadataId)
        assertFalse(models[0].isSoftDeleted)
    }

    @Test
    fun `availableDownloads is empty when no models are soft-deleted`() {
        val assets = listOf(
            localAsset(id = "model-1", withConfig = true),
            localAsset(id = "model-2", withConfig = true),
        )

        val state = factory.create(localAssets = assets, defaults = defaultFactoryArgs())

        assertTrue(state.localModelsSheet.availableDownloads.isEmpty())
    }

    @Test
    fun `models is empty when all models are soft-deleted`() {
        val assets = listOf(
            localAsset(id = "model-1", withConfig = false),
            localAsset(id = "model-2", withConfig = false),
        )

        val state = factory.create(localAssets = assets, defaults = defaultFactoryArgs())

        assertTrue(state.localModelsSheet.models.isEmpty())
        assertEquals(2, state.localModelsSheet.availableDownloads.size)
    }

    @Test
    fun `soft-deleted model does not appear in models section`() {
        val softDeletedModel = localAsset(id = "ghost", withConfig = false)

        val state = factory.create(localAssets = listOf(softDeletedModel), defaults = defaultFactoryArgs())

        val inDownloaded = state.localModelsSheet.models.any { it.metadataId == LocalModelId("ghost") }
        assertFalse(inDownloaded)
    }

    @Test
    fun `downloaded model does not appear in availableDownloads section`() {
        val downloadedModel = localAsset(id = "present", withConfig = true)

        val state = factory.create(localAssets = listOf(downloadedModel), defaults = defaultFactoryArgs())

        val inDownloads = state.localModelsSheet.availableDownloads.any { it.metadataId == LocalModelId("present") }
        assertFalse(inDownloads)
    }

    // endregion

    // region helpers

    private fun localAsset(id: String, withConfig: Boolean): LocalModelAsset {
        val modelId = LocalModelId(id)
        val metadata = LocalModelMetadata(
            id = modelId,
            huggingFaceModelName = "test/$id",
            remoteFileName = "$id.gguf",
            localFileName = "$id.gguf",
            sha256 = "abc",
            sizeInBytes = 1_000_000L,
            modelFileFormat = ModelFileFormat.GGUF,
        )
        val configs = if (withConfig) {
            listOf(
                LocalModelConfiguration(
                    id = LocalModelConfigurationId("${id}-cfg"),
                    localModelId = modelId,
                    displayName = "Default",
                    maxTokens = 4096,
                    contextWindow = 4096,
                    temperature = 0.7,
                    topP = 0.95,
                    topK = 40,
                    repetitionPenalty = 1.1,
                    systemPrompt = "",
                )
            )
        } else {
            emptyList()
        }
        return LocalModelAsset(metadata = metadata, configurations = configs)
    }

    private data class FactoryArgs(
        val persistedSettings: SettingsData = SettingsData(),
        val localModelsState: LocalModelsTransientState = LocalModelsTransientState(),
    )

    private fun defaultFactoryArgs() = FactoryArgs()

    private fun SettingsUiStateFactory.create(
        localAssets: List<LocalModelAsset>,
        defaults: FactoryArgs,
    ) = create(
        persistedSettings = defaults.persistedSettings,
        localAssets = localAssets,
        apiAssets = emptyList(),
        ttsAssets = emptyList(),
        mediaAssets = emptyList(),
        defaultModels = emptyList(),
        authState = AuthState.Loading,
        sheetVisibility = SheetVisibilityState(),
        memories = emptyList(),
        feedbackText = "",
        localModelsState = defaults.localModelsState,
        apiState = ApiProvidersTransientState(),
        ttsState = TtsProvidersTransientState(),
        mediaState = MediaProvidersTransientState(),
        searchSkillState = SearchSkillTransientState(),
        assignmentsState = AssignmentDialogTransientState(),
        deletionState = DeletionTransientState(),
        memoriesState = MemoriesTransientState(),
        currentTavilyApiKey = "",
        currentExaApiKey = "",
        currentXApiKey = "",
        xSearchState = XSearchTransientState(),
        isReindexing = false,
        reindexProgress = 0f,
        vaultReauthSheet = VaultReauthSheetState.Hidden,
    )

    // endregion
}
