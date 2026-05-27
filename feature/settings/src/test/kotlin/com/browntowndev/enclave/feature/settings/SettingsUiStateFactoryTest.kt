package com.browntowndev.enclave.feature.settings

import com.browntowndev.enclave.domain.model.config.DefaultModelAssignment
import com.browntowndev.enclave.domain.model.config.LocalModelAsset
import com.browntowndev.enclave.domain.model.config.LocalModelConfiguration
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelId
import com.browntowndev.enclave.domain.model.config.LocalModelMetadata
import com.browntowndev.enclave.domain.model.inference.ModelFileFormat
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.telemetry.UsagePeriod
import com.browntowndev.enclave.domain.model.telemetry.UsageSpendSummary
import com.browntowndev.enclave.domain.port.auth.AuthState
import com.browntowndev.enclave.domain.port.auth.UserProfile
import com.browntowndev.enclave.domain.port.repository.SettingsData
import com.browntowndev.enclave.domain.usecase.settings.ApplyApiModelMetadataDefaultsUseCase
import com.browntowndev.enclave.feature.settings.usage.UsageSpendUiMapper
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class SettingsUiStateFactoryTest {
    private lateinit var factory: SettingsUiStateFactory

    @BeforeEach
    fun setup() {
        factory =
            SettingsUiStateFactory(
                localModelAssetUiMapper = LocalModelAssetUiMapper(),
                apiModelAssetUiMapper = ApiModelAssetUiMapper(),
                ttsProviderAssetUiMapper = TtsProviderAssetUiMapper(),
                mediaProviderAssetUiMapper = mockk(relaxed = true),
                apiDiscoveryUiFilter = ApiDiscoveryUiFilter(),
                applyApiModelMetadataDefaultsUseCase = ApplyApiModelMetadataDefaultsUseCase(),
                usageSpendUiMapper = UsageSpendUiMapper(),
            )
    }

    @Test
    fun `SCN-UI-002 settings ui state factory exposes friendly asset name`() {
        val localAsset =
            LocalModelAsset(
                metadata =
                    LocalModelMetadata(
                        id = LocalModelId("gemma"),
                        displayName = "Gemma 4 E4B",
                        huggingFaceModelName = "litert-community/gemma-4-E4B-it-litert-lm",
                        remoteFileName = "gemma-4-E4B-it.litertlm",
                        localFileName = "gemma-4-E4B-it.litertlm",
                        sha256 = "gemma-sha",
                        sizeInBytes = 100L,
                        modelFileFormat = ModelFileFormat.LITERTLM,
                    ),
                configurations =
                    listOf(
                        LocalModelConfiguration(
                            id = LocalModelConfigurationId("cfg-fast"),
                            localModelId = LocalModelId("gemma"),
                            displayName = "Fast",
                            maxTokens = 2048,
                            contextWindow = 8192,
                            temperature = 0.7,
                            topP = 0.9,
                            topK = 40,
                            repetitionPenalty = 1.0,
                            systemPrompt = "",
                        ),
                    ),
            )

        val state = factory.create(localAssets = listOf(localAsset), defaults = defaultFactoryArgs())

        assertEquals("Gemma 4 E4B", state.localModelsSheet.models[0].friendlyName)
    }

    // region availableDownloads filtering

    @Test
    fun `availableDownloads contains only soft-deleted models`() {
        val downloadedModel = localAsset(id = "downloaded", withConfig = true)
        val softDeletedModel = localAsset(id = "soft-deleted", withConfig = false)

        val state =
            factory.create(
                localAssets = listOf(downloadedModel, softDeletedModel),
                defaults = defaultFactoryArgs(),
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

        val state =
            factory.create(
                localAssets = listOf(downloadedModel, softDeletedModel),
                defaults = defaultFactoryArgs(),
            )

        val models = state.localModelsSheet.models
        assertEquals(1, models.size)
        assertEquals(LocalModelId("downloaded"), models[0].metadataId)
        assertFalse(models[0].isSoftDeleted)
    }

    @Test
    fun `availableDownloads is empty when no models are soft-deleted`() {
        val assets =
            listOf(
                localAsset(id = "model-1", withConfig = true),
                localAsset(id = "model-2", withConfig = true),
            )

        val state = factory.create(localAssets = assets, defaults = defaultFactoryArgs())

        assertTrue(state.localModelsSheet.availableDownloads.isEmpty())
    }

    @Test
    fun `models is empty when all models are soft-deleted`() {
        val assets =
            listOf(
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

    @Test
    fun `signed in auth state maps user profile to home auth ui`() {
        val profile =
            UserProfile(
                uid = "uid-42",
                displayName = "Alice",
                email = "alice@example.com",
                photoUrl = "https://example.com/photo.png",
            )

        val state =
            factory.create(
                localAssets = emptyList(),
                defaults = defaultFactoryArgs(),
                authState = AuthState.Authenticated(profile),
            )

        val auth = state.home.auth
        assertTrue(auth is AuthStateUi.Authenticated)
        auth as AuthStateUi.Authenticated
        assertEquals("uid-42", auth.user.uid)
        assertEquals("Alice", auth.user.displayName)
        assertEquals("alice@example.com", auth.user.email)
        assertEquals("https://example.com/photo.png", auth.user.photoUrl)
    }

    @Test
    fun `default model assignments expose slot labels and none assigned placeholders`() {
        val state =
            factory.create(
                localAssets = emptyList(),
                defaults = defaultFactoryArgs(),
                defaultModels =
                    listOf(
                        DefaultModelAssignment(
                            modelType = ModelType.FAST,
                            displayName = "Gemma Fast",
                        ),
                    ),
            )

        val fast =
            state.assignments.assignments.single { it.modelType == ModelType.FAST }
        assertEquals("Fast", fast.displayLabel)
        assertEquals("Gemma Fast", fast.currentModelName)

        val tts =
            state.assignments.assignments.single { it.modelType == ModelType.TTS }
        assertEquals("Text-to-Speech", tts.displayLabel)
        assertEquals("None Assigned", tts.currentModelName)

        val vision =
            state.assignments.assignments.single { it.modelType == ModelType.VISION }
        assertEquals("Vision (API)", vision.displayLabel)
        assertEquals("None Assigned", vision.currentModelName)
    }

    // endregion

    // region helpers

    private fun localAsset(
        id: String,
        withConfig: Boolean,
    ): LocalModelAsset {
        val modelId = LocalModelId(id)
        val metadata =
            LocalModelMetadata(
                id = modelId,
                huggingFaceModelName = "test/$id",
                remoteFileName = "$id.gguf",
                localFileName = "$id.gguf",
                sha256 = "abc",
                sizeInBytes = 1_000_000L,
                modelFileFormat = ModelFileFormat.GGUF,
            )
        val configs =
            if (withConfig) {
                listOf(
                    LocalModelConfiguration(
                        id = LocalModelConfigurationId("$id-cfg"),
                        localModelId = modelId,
                        displayName = "Default",
                        maxTokens = 4096,
                        contextWindow = 4096,
                        temperature = 0.7,
                        topP = 0.95,
                        topK = 40,
                        repetitionPenalty = 1.1,
                        systemPrompt = "",
                    ),
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
        defaultModels: List<DefaultModelAssignment> = emptyList(),
        authState: AuthState = AuthState.Loading,
    ) = create(
        persistedSettings = defaults.persistedSettings,
        localAssets = localAssets,
        apiAssets = emptyList(),
        ttsAssets = emptyList(),
        mediaAssets = emptyList(),
        defaultModels = defaultModels,
        authState = authState,
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
        dataControlsState = DataControlsTransientState(),
        currentTavilyApiKey = "",
        currentExaApiKey = "",
        currentXApiKey = "",
        xSearchState = XSearchTransientState(),
        isReindexing = false,
        reindexProgress = 0f,
        vaultReauthSheet = VaultReauthSheetState.Hidden,
        usageSpendSummary =
            UsageSpendSummary(
                period = UsagePeriod.LIFETIME,
                periodStartUtcMs = null,
                periodEndUtcMs = null,
                totalCostUsd = BigDecimal.ZERO,
                telemetryBackedTurnCount = 0,
                missingCostTurnCount = 0,
                providerBreakdown = emptyList(),
            ),
    )

    // endregion
}
