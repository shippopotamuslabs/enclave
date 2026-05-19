@file:Suppress(
    "MaxLineLength",
    "MagicNumber",
    "UnusedPrivateProperty",
    "UnusedParameter",
    "TooManyFunctions",
    "ComplexCondition",
    "LargeClass",
    "CyclomaticComplexMethod",
    "SwallowedException",
    "InstanceOfCheckForException",
    "ThrowsCount",
    "NestedBlockDepth",
    "ReturnCount",
    "LoopWithTooManyJumpStatements",
    "FunctionNaming",
    "LongMethod",
    "MatchingDeclarationName",
    "UnusedPrivateMember",
    "LongParameterList",
)

package com.browntowndev.pocketcrew.domain.usecase.settings

import com.browntowndev.pocketcrew.domain.usecase.auth.GetAuthStateUseCase
import com.browntowndev.pocketcrew.domain.usecase.auth.LoginWithGoogleUseCase
import com.browntowndev.pocketcrew.domain.usecase.auth.LogoutUseCase
import com.browntowndev.pocketcrew.domain.usecase.byok.ClearDefaultModelUseCase
import com.browntowndev.pocketcrew.domain.usecase.byok.GetApiModelAssetsUseCase
import com.browntowndev.pocketcrew.domain.usecase.byok.GetDefaultModelsUseCase
import com.browntowndev.pocketcrew.domain.usecase.byok.SetDefaultModelUseCase
import com.browntowndev.pocketcrew.domain.usecase.media.DeleteMediaProviderUseCase
import com.browntowndev.pocketcrew.domain.usecase.media.GetMediaProvidersUseCase
import com.browntowndev.pocketcrew.domain.usecase.media.SaveMediaProviderUseCase
import com.browntowndev.pocketcrew.domain.usecase.modelconfig.GetLocalModelAssetsUseCase
import com.browntowndev.pocketcrew.domain.usecase.modelconfig.ReDownloadModelUseCase

interface SettingsPreferencesUseCases {
    val getSettings: GetSettingsUseCase
    val updateTheme: UpdateThemeUseCase
    val updateHapticResponse: UpdateHapticResponseUseCase
    val updateCustomizationEnabled: UpdateCustomizationEnabledUseCase
    val updateSelectedPromptOption: UpdateSelectedPromptOptionUseCase
    val updateCustomPromptText: UpdateCustomPromptTextUseCase
    val updateAllowMemories: UpdateAllowMemoriesUseCase
    val updateSearchEnabled: UpdateSearchEnabledUseCase
    val updateSearchProvider: UpdateSearchProviderUseCase
    val saveTavilyApiKey: SaveTavilyApiKeyUseCase
    val clearTavilyApiKey: ClearTavilyApiKeyUseCase
    val saveExaApiKey: SaveExaApiKeyUseCase
    val clearExaApiKey: ClearExaApiKeyUseCase
    val updateXSearchEnabled: UpdateXSearchEnabledUseCase
    val saveXApiKey: SaveXApiKeyUseCase
    val clearXApiKey: ClearXApiKeyUseCase
    val updateShowSearchProviderSwitchAlert: UpdateShowSearchProviderSwitchAlertUseCase
    val updateBackgroundInferenceEnabled: UpdateBackgroundInferenceEnabledUseCase
}

interface SettingsLocalModelUseCases {
    val getLocalModelAssets: GetLocalModelAssetsUseCase
    val getRestorableLocalModels: GetRestorableLocalModelsUseCase
    val saveLocalModelPreset: SaveLocalModelPresetUseCase
    val reDownloadModel: ReDownloadModelUseCase
}

interface SettingsApiProviderUseCases {
    val getApiModelAssets: GetApiModelAssetsUseCase
    val saveApiProviderDraft: SaveApiProviderDraftUseCase
    val saveApiPreset: SaveApiPresetUseCase
    val discoverApiModels: DiscoverApiModelsUseCase
    val applyApiModelMetadataDefaults: ApplyApiModelMetadataDefaultsUseCase
}

interface SettingsAssignmentUseCases {
    val getDefaultModels: GetDefaultModelsUseCase
    val setDefaultModel: SetDefaultModelUseCase
    val clearDefaultModel: ClearDefaultModelUseCase
    val resolveAssignedModelSelection: ResolveAssignedModelSelectionUseCase
}

interface SettingsDeletionUseCases {
    val prepareModelDeletion: PrepareModelDeletionUseCase
    val executeModelDeletionWithReassignment: ExecuteModelDeletionWithReassignmentUseCase
}

interface SettingsTtsUseCases {
    val getTtsProviders: GetTtsProvidersUseCase
    val saveTtsProvider: SaveTtsProviderUseCase
    val deleteTtsProvider: DeleteTtsProviderUseCase
}

interface SettingsMediaUseCases {
    val getMediaProviders: GetMediaProvidersUseCase
    val saveMediaProvider: SaveMediaProviderUseCase
    val deleteMediaProvider: DeleteMediaProviderUseCase
}

interface SettingsAuthUseCases {
    val loginWithGoogle: LoginWithGoogleUseCase
    val logout: LogoutUseCase
    val getAuthState: GetAuthStateUseCase
}

interface SettingsMemoriesUseCases {
    fun getAllMemoriesFlow(): kotlinx.coroutines.flow.Flow<List<com.browntowndev.pocketcrew.domain.model.memory.Memory>>

    suspend fun insertMemory(
        category: com.browntowndev.pocketcrew.domain.model.memory.MemoryCategory,
        content: String,
    ): com.browntowndev.pocketcrew.domain.model.memory.Memory

    suspend fun updateMemory(
        id: String,
        content: String,
        category: com.browntowndev.pocketcrew.domain.model.memory.MemoryCategory?,
    )

    suspend fun deleteMemory(id: String)
}

interface SettingsDataControlsUseCases {
    val deleteAllChats: DeleteAllChatsUseCase
    val deleteAllMedia: DeleteAllMediaUseCase
    val deleteAllMemories: DeleteAllMemoriesUseCase
    val deleteAllArtifacts: DeleteAllArtifactsUseCase
    val deleteAccount: DeleteAccountUseCase
}

interface SettingsUseCases {
    val preferences: SettingsPreferencesUseCases
    val localModels: SettingsLocalModelUseCases
    val apiProviders: SettingsApiProviderUseCases
    val assignments: SettingsAssignmentUseCases
    val deletion: SettingsDeletionUseCases
    val tts: SettingsTtsUseCases
    val media: SettingsMediaUseCases
    val auth: SettingsAuthUseCases
    val memories: SettingsMemoriesUseCases
    val dataControls: SettingsDataControlsUseCases

    val getSettings: GetSettingsUseCase
        get() = preferences.getSettings
}
