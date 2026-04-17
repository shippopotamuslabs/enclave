package com.browntowndev.pocketcrew.domain.usecase.settings

import com.browntowndev.pocketcrew.domain.usecase.byok.GetApiModelAssetsUseCase
import com.browntowndev.pocketcrew.domain.usecase.byok.GetDefaultModelsUseCase
import com.browntowndev.pocketcrew.domain.usecase.byok.SetDefaultModelUseCase
import com.browntowndev.pocketcrew.domain.usecase.modelconfig.GetLocalModelAssetsUseCase
import com.browntowndev.pocketcrew.domain.usecase.modelconfig.ReDownloadModelUseCase

interface SettingsPreferencesUseCases {
    val getSettings: GetSettingsUseCase
    val updateTheme: UpdateThemeUseCase
    val updateHapticPress: UpdateHapticPressUseCase
    val updateHapticResponse: UpdateHapticResponseUseCase
    val updateCustomizationEnabled: UpdateCustomizationEnabledUseCase
    val updateSelectedPromptOption: UpdateSelectedPromptOptionUseCase
    val updateCustomPromptText: UpdateCustomPromptTextUseCase
    val updateAllowMemories: UpdateAllowMemoriesUseCase
    val updateSearchEnabled: UpdateSearchEnabledUseCase
    val updateAlwaysUseVisionModel: UpdateAlwaysUseVisionModelUseCase
    val updateCompactionProviderType: UpdateCompactionProviderTypeUseCase
    val updateCompactionApiModelId: UpdateCompactionApiModelIdUseCase
    val saveTavilyApiKey: SaveTavilyApiKeyUseCase
    val clearTavilyApiKey: ClearTavilyApiKeyUseCase
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
    val resolveAssignedModelSelection: ResolveAssignedModelSelectionUseCase
}

interface SettingsDeletionUseCases {
    val prepareModelDeletion: PrepareModelDeletionUseCase
    val executeModelDeletionWithReassignment: ExecuteModelDeletionWithReassignmentUseCase
}

interface SettingsUseCases {
    val preferences: SettingsPreferencesUseCases
    val localModels: SettingsLocalModelUseCases
    val apiProviders: SettingsApiProviderUseCases
    val assignments: SettingsAssignmentUseCases
    val deletion: SettingsDeletionUseCases

    val getSettings: GetSettingsUseCase
        get() = preferences.getSettings
}
