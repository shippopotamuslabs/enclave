package com.browntowndev.pocketcrew.domain.usecase.settings

import com.browntowndev.pocketcrew.domain.usecase.byok.GetApiModelAssetsUseCase
import com.browntowndev.pocketcrew.domain.usecase.byok.GetDefaultModelsUseCase
import com.browntowndev.pocketcrew.domain.usecase.byok.SetDefaultModelUseCase
import com.browntowndev.pocketcrew.domain.usecase.modelconfig.GetLocalModelAssetsUseCase
import com.browntowndev.pocketcrew.domain.usecase.modelconfig.ReDownloadModelUseCase
import javax.inject.Inject

class SettingsPreferencesUseCasesImpl @Inject constructor(
    override val getSettings: GetSettingsUseCase,
    override val updateTheme: UpdateThemeUseCase,
    override val updateHapticPress: UpdateHapticPressUseCase,
    override val updateHapticResponse: UpdateHapticResponseUseCase,
    override val updateCustomizationEnabled: UpdateCustomizationEnabledUseCase,
    override val updateSelectedPromptOption: UpdateSelectedPromptOptionUseCase,
    override val updateCustomPromptText: UpdateCustomPromptTextUseCase,
    override val updateAllowMemories: UpdateAllowMemoriesUseCase,
    override val updateSearchEnabled: UpdateSearchEnabledUseCase,
    override val saveTavilyApiKey: SaveTavilyApiKeyUseCase,
    override val clearTavilyApiKey: ClearTavilyApiKeyUseCase,
    override val updateBackgroundInferenceEnabled: UpdateBackgroundInferenceEnabledUseCase,
) : SettingsPreferencesUseCases

class SettingsLocalModelUseCasesImpl @Inject constructor(
    override val getLocalModelAssets: GetLocalModelAssetsUseCase,
    override val getRestorableLocalModels: GetRestorableLocalModelsUseCase,
    override val saveLocalModelPreset: SaveLocalModelPresetUseCase,
    override val reDownloadModel: ReDownloadModelUseCase,
) : SettingsLocalModelUseCases

class SettingsApiProviderUseCasesImpl @Inject constructor(
    override val getApiModelAssets: GetApiModelAssetsUseCase,
    override val saveApiProviderDraft: SaveApiProviderDraftUseCase,
    override val saveApiPreset: SaveApiPresetUseCase,
    override val discoverApiModels: DiscoverApiModelsUseCase,
    override val applyApiModelMetadataDefaults: ApplyApiModelMetadataDefaultsUseCase,
) : SettingsApiProviderUseCases

class SettingsAssignmentUseCasesImpl @Inject constructor(
    override val getDefaultModels: GetDefaultModelsUseCase,
    override val setDefaultModel: SetDefaultModelUseCase,
    override val resolveAssignedModelSelection: ResolveAssignedModelSelectionUseCase,
) : SettingsAssignmentUseCases

class SettingsDeletionUseCasesImpl @Inject constructor(
    override val prepareModelDeletion: PrepareModelDeletionUseCase,
    override val executeModelDeletionWithReassignment: ExecuteModelDeletionWithReassignmentUseCase,
) : SettingsDeletionUseCases

class SettingsTtsUseCasesImpl @Inject constructor(
    override val getTtsProviders: GetTtsProvidersUseCase,
    override val saveTtsProvider: SaveTtsProviderUseCase,
    override val deleteTtsProvider: DeleteTtsProviderUseCase,
) : SettingsTtsUseCases

class SettingsUseCasesImpl @Inject constructor(
    override val preferences: SettingsPreferencesUseCases,
    override val localModels: SettingsLocalModelUseCases,
    override val apiProviders: SettingsApiProviderUseCases,
    override val assignments: SettingsAssignmentUseCases,
    override val deletion: SettingsDeletionUseCases,
    override val tts: SettingsTtsUseCases,
) : SettingsUseCases
