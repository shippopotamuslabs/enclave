package com.browntowndev.enclave.domain.usecase.settings

import com.browntowndev.enclave.core.testing.FakeSettingsRepository
import com.browntowndev.enclave.domain.model.memory.Memory
import com.browntowndev.enclave.domain.model.memory.MemoryCategory
import com.browntowndev.enclave.domain.usecase.auth.GetAuthStateUseCase
import com.browntowndev.enclave.domain.usecase.auth.LoginWithGoogleUseCase
import com.browntowndev.enclave.domain.usecase.auth.LogoutUseCase
import com.browntowndev.enclave.domain.usecase.byok.ClearDefaultModelUseCase
import com.browntowndev.enclave.domain.usecase.byok.FetchOpenRouterModelEndpointsUseCase
import com.browntowndev.enclave.domain.usecase.byok.GetApiModelAssetsUseCase
import com.browntowndev.enclave.domain.usecase.byok.GetDefaultModelsUseCase
import com.browntowndev.enclave.domain.usecase.byok.SetDefaultModelUseCase
import com.browntowndev.enclave.domain.usecase.media.DeleteMediaProviderUseCase
import com.browntowndev.enclave.domain.usecase.media.GetMediaProvidersUseCase
import com.browntowndev.enclave.domain.usecase.media.SaveMediaProviderUseCase
import com.browntowndev.enclave.domain.usecase.modelconfig.DeleteModelPackUseCase
import com.browntowndev.enclave.domain.usecase.modelconfig.GetLocalModelAssetsUseCase
import com.browntowndev.enclave.domain.usecase.modelconfig.GetModelPacksForSettingsUseCase
import com.browntowndev.enclave.domain.usecase.modelconfig.ObserveActiveUtilityModelsUseCase
import com.browntowndev.enclave.domain.usecase.modelconfig.ReDownloadModelPackUseCase
import com.browntowndev.enclave.domain.usecase.modelconfig.ReDownloadModelUseCase
import kotlinx.coroutines.flow.Flow
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/** Hand-written group stubs: every member except the one under test throws if touched. */
private fun <T> unused(): T = throw UnsupportedOperationException("Stub group member must not be invoked by this test")

private class StubSettingsPreferencesUseCases(
    override val getSettings: GetSettingsUseCase,
) : SettingsPreferencesUseCases {
    override val updateTheme: UpdateThemeUseCase get() = unused()
    override val updateHapticResponse: UpdateHapticResponseUseCase get() = unused()
    override val updateCustomizationEnabled: UpdateCustomizationEnabledUseCase get() = unused()
    override val updateSelectedPromptOption: UpdateSelectedPromptOptionUseCase get() = unused()
    override val updateCustomPromptText: UpdateCustomPromptTextUseCase get() = unused()
    override val updateAllowMemories: UpdateAllowMemoriesUseCase get() = unused()
    override val updateSearchEnabled: UpdateSearchEnabledUseCase get() = unused()
    override val updateSearchProvider: UpdateSearchProviderUseCase get() = unused()
    override val saveTavilyApiKey: SaveTavilyApiKeyUseCase get() = unused()
    override val clearTavilyApiKey: ClearTavilyApiKeyUseCase get() = unused()
    override val saveExaApiKey: SaveExaApiKeyUseCase get() = unused()
    override val clearExaApiKey: ClearExaApiKeyUseCase get() = unused()
    override val updateXSearchEnabled: UpdateXSearchEnabledUseCase get() = unused()
    override val saveXApiKey: SaveXApiKeyUseCase get() = unused()
    override val clearXApiKey: ClearXApiKeyUseCase get() = unused()
    override val updateShowSearchProviderSwitchAlert: UpdateShowSearchProviderSwitchAlertUseCase get() = unused()
    override val updateBackgroundInferenceEnabled: UpdateBackgroundInferenceEnabledUseCase get() = unused()
    override val updateAutomaticErrorTrackingEnabled: UpdateAutomaticErrorTrackingEnabledUseCase get() = unused()
}

private class StubSettingsLocalModelUseCases : SettingsLocalModelUseCases {
    override val getLocalModelAssets: GetLocalModelAssetsUseCase get() = unused()
    override val getRestorableLocalModels: GetRestorableLocalModelsUseCase get() = unused()
    override val saveLocalModelPreset: SaveLocalModelPresetUseCase get() = unused()
    override val reDownloadModel: ReDownloadModelUseCase get() = unused()
    override val getModelPacksForSettings: GetModelPacksForSettingsUseCase get() = unused()
    override val observeActiveUtilityModels: ObserveActiveUtilityModelsUseCase get() = unused()
    override val reDownloadModelPack: ReDownloadModelPackUseCase get() = unused()
    override val deleteModelPack: DeleteModelPackUseCase get() = unused()
}

private class StubSettingsApiProviderUseCases : SettingsApiProviderUseCases {
    override val getApiModelAssets: GetApiModelAssetsUseCase get() = unused()
    override val saveApiProviderDraft: SaveApiProviderDraftUseCase get() = unused()
    override val saveApiPreset: SaveApiPresetUseCase get() = unused()
    override val discoverApiModels: DiscoverApiModelsUseCase get() = unused()
    override val applyApiModelMetadataDefaults: ApplyApiModelMetadataDefaultsUseCase get() = unused()
    override val testApiProviderConnection: TestApiProviderConnectionUseCase get() = unused()
    override val fetchOpenRouterModelEndpoints: FetchOpenRouterModelEndpointsUseCase get() = unused()
}

private class StubSettingsAssignmentUseCases : SettingsAssignmentUseCases {
    override val getDefaultModels: GetDefaultModelsUseCase get() = unused()
    override val setDefaultModel: SetDefaultModelUseCase get() = unused()
    override val clearDefaultModel: ClearDefaultModelUseCase get() = unused()
    override val resolveAssignedModelSelection: ResolveAssignedModelSelectionUseCase get() = unused()
}

private class StubSettingsDeletionUseCases : SettingsDeletionUseCases {
    override val prepareModelDeletion: PrepareModelDeletionUseCase get() = unused()
    override val executeModelDeletionWithReassignment: ExecuteModelDeletionWithReassignmentUseCase get() = unused()
}

private class StubSettingsTtsUseCases : SettingsTtsUseCases {
    override val getTtsProviders: GetTtsProvidersUseCase get() = unused()
    override val saveTtsProvider: SaveTtsProviderUseCase get() = unused()
    override val deleteTtsProvider: DeleteTtsProviderUseCase get() = unused()
}

private class StubSettingsMediaUseCases : SettingsMediaUseCases {
    override val getMediaProviders: GetMediaProvidersUseCase get() = unused()
    override val saveMediaProvider: SaveMediaProviderUseCase get() = unused()
    override val deleteMediaProvider: DeleteMediaProviderUseCase get() = unused()
}

private class StubSettingsAuthUseCases : SettingsAuthUseCases {
    override val loginWithGoogle: LoginWithGoogleUseCase get() = unused()
    override val logout: LogoutUseCase get() = unused()
    override val getAuthState: GetAuthStateUseCase get() = unused()
}

private class StubSettingsMemoriesUseCases : SettingsMemoriesUseCases {
    override fun getAllMemoriesFlow(): Flow<List<Memory>> = unused()

    override suspend fun insertMemory(
        category: MemoryCategory,
        content: String,
    ): Memory = unused()

    override suspend fun updateMemory(
        id: String,
        content: String,
        category: MemoryCategory?,
    ): Unit = unused()

    override suspend fun deleteMemory(id: String): Unit = unused()
}

private class StubSettingsDataControlsUseCases : SettingsDataControlsUseCases {
    override val deleteAllChats: DeleteAllChatsUseCase get() = unused()
    override val deleteAllMedia: DeleteAllMediaUseCase get() = unused()
    override val deleteAllMemories: DeleteAllMemoriesUseCase get() = unused()
    override val deleteAllArtifacts: DeleteAllArtifactsUseCase get() = unused()
    override val deleteAccount: DeleteAccountUseCase get() = unused()
}

class SettingsUseCasesImplTest {
    @Test
    fun `root settings use cases expose grouped collaborators and getSettings shortcut`() {
        val getSettings = GetSettingsUseCase(FakeSettingsRepository())
        val preferences = StubSettingsPreferencesUseCases(getSettings)
        val localModels = StubSettingsLocalModelUseCases()
        val apiProviders = StubSettingsApiProviderUseCases()
        val assignments = StubSettingsAssignmentUseCases()
        val deletion = StubSettingsDeletionUseCases()
        val tts = StubSettingsTtsUseCases()
        val media = StubSettingsMediaUseCases()
        val auth = StubSettingsAuthUseCases()
        val memories = StubSettingsMemoriesUseCases()
        val dataControls = StubSettingsDataControlsUseCases()

        val useCases =
            SettingsUseCasesImpl(
                preferences = preferences,
                localModels = localModels,
                apiProviders = apiProviders,
                assignments = assignments,
                deletion = deletion,
                tts = tts,
                media = media,
                auth = auth,
                memories = memories,
                dataControls = dataControls,
            )

        assertSame(preferences, useCases.preferences)
        assertSame(localModels, useCases.localModels)
        assertSame(apiProviders, useCases.apiProviders)
        assertSame(assignments, useCases.assignments)
        assertSame(deletion, useCases.deletion)
        assertSame(tts, useCases.tts)
        assertSame(media, useCases.media)
        assertSame(auth, useCases.auth)
        assertSame(memories, useCases.memories)
        assertSame(dataControls, useCases.dataControls)
        assertSame(getSettings, useCases.getSettings)
    }
}
