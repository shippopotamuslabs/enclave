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

package com.browntowndev.enclave.domain.usecase.settings

import com.browntowndev.enclave.domain.usecase.auth.GetAuthStateUseCase
import com.browntowndev.enclave.domain.usecase.auth.LoginWithGoogleUseCase
import com.browntowndev.enclave.domain.usecase.auth.LogoutUseCase
import com.browntowndev.enclave.domain.usecase.byok.ClearDefaultModelUseCase
import com.browntowndev.enclave.domain.usecase.byok.GetApiModelAssetsUseCase
import com.browntowndev.enclave.domain.usecase.byok.GetDefaultModelsUseCase
import com.browntowndev.enclave.domain.usecase.byok.SetDefaultModelUseCase
import com.browntowndev.enclave.domain.usecase.media.DeleteMediaProviderUseCase
import com.browntowndev.enclave.domain.usecase.media.GetMediaProvidersUseCase
import com.browntowndev.enclave.domain.usecase.media.SaveMediaProviderUseCase
import com.browntowndev.enclave.domain.model.config.LiveVoiceProviderAsset
import com.browntowndev.enclave.domain.model.config.LiveVoiceProviderId
import com.browntowndev.enclave.domain.port.repository.LiveVoiceProviderRepositoryPort
import com.browntowndev.enclave.domain.usecase.modelconfig.GetLocalModelAssetsUseCase
import com.browntowndev.enclave.domain.usecase.modelconfig.ReDownloadModelUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

class SettingsPreferencesUseCasesImpl
    @Inject
    constructor(
        override val getSettings: GetSettingsUseCase,
        override val updateTheme: UpdateThemeUseCase,
        override val updateHapticResponse: UpdateHapticResponseUseCase,
        override val updateCustomizationEnabled: UpdateCustomizationEnabledUseCase,
        override val updateSelectedPromptOption: UpdateSelectedPromptOptionUseCase,
        override val updateCustomPromptText: UpdateCustomPromptTextUseCase,
        override val updateAllowMemories: UpdateAllowMemoriesUseCase,
        override val updateSearchEnabled: UpdateSearchEnabledUseCase,
        override val updateSearchProvider: UpdateSearchProviderUseCase,
        override val saveTavilyApiKey: SaveTavilyApiKeyUseCase,
        override val clearTavilyApiKey: ClearTavilyApiKeyUseCase,
        override val saveExaApiKey: SaveExaApiKeyUseCase,
        override val clearExaApiKey: ClearExaApiKeyUseCase,
        override val updateXSearchEnabled: UpdateXSearchEnabledUseCase,
        override val saveXApiKey: SaveXApiKeyUseCase,
        override val clearXApiKey: ClearXApiKeyUseCase,
        override val updateShowSearchProviderSwitchAlert: UpdateShowSearchProviderSwitchAlertUseCase,
        override val updateBackgroundInferenceEnabled: UpdateBackgroundInferenceEnabledUseCase,
        override val updateAutomaticErrorTrackingEnabled: UpdateAutomaticErrorTrackingEnabledUseCase,
    ) : SettingsPreferencesUseCases

class SettingsLocalModelUseCasesImpl
    @Inject
    constructor(
        override val getLocalModelAssets: GetLocalModelAssetsUseCase,
        override val getRestorableLocalModels: GetRestorableLocalModelsUseCase,
        override val saveLocalModelPreset: SaveLocalModelPresetUseCase,
        override val reDownloadModel: ReDownloadModelUseCase,
        override val getModelPacksForSettings: com.browntowndev.enclave.domain.usecase.modelconfig.GetModelPacksForSettingsUseCase,
        override val observeActiveUtilityModels: com.browntowndev.enclave.domain.usecase.modelconfig.ObserveActiveUtilityModelsUseCase,
        override val reDownloadModelPack: com.browntowndev.enclave.domain.usecase.modelconfig.ReDownloadModelPackUseCase,
        override val deleteModelPack: com.browntowndev.enclave.domain.usecase.modelconfig.DeleteModelPackUseCase,
    ) : SettingsLocalModelUseCases

class SettingsApiProviderUseCasesImpl
    @Inject
    constructor(
        override val getApiModelAssets: GetApiModelAssetsUseCase,
        override val saveApiProviderDraft: SaveApiProviderDraftUseCase,
        override val saveApiPreset: SaveApiPresetUseCase,
        override val discoverApiModels: DiscoverApiModelsUseCase,
        override val applyApiModelMetadataDefaults: ApplyApiModelMetadataDefaultsUseCase,
        override val testApiProviderConnection: TestApiProviderConnectionUseCase,
        override val fetchOpenRouterModelEndpoints: com.browntowndev.enclave.domain.usecase.byok.FetchOpenRouterModelEndpointsUseCase,
    ) : SettingsApiProviderUseCases

class SettingsAssignmentUseCasesImpl
    @Inject
    constructor(
        override val getDefaultModels: GetDefaultModelsUseCase,
        override val setDefaultModel: SetDefaultModelUseCase,
        override val clearDefaultModel: ClearDefaultModelUseCase,
        override val resolveAssignedModelSelection: ResolveAssignedModelSelectionUseCase,
    ) : SettingsAssignmentUseCases

class SettingsDeletionUseCasesImpl
    @Inject
    constructor(
        override val prepareModelDeletion: PrepareModelDeletionUseCase,
        override val executeModelDeletionWithReassignment: ExecuteModelDeletionWithReassignmentUseCase,
    ) : SettingsDeletionUseCases

class SettingsTtsUseCasesImpl
    @Inject
    constructor(
        override val getTtsProviders: GetTtsProvidersUseCase,
        override val saveTtsProvider: SaveTtsProviderUseCase,
        override val deleteTtsProvider: DeleteTtsProviderUseCase,
    ) : SettingsTtsUseCases

class SettingsLiveVoiceUseCasesImpl
    @Inject
    constructor(
        override val getLiveVoiceProviders: GetLiveVoiceProvidersUseCase,
        override val saveLiveVoiceProvider: SaveLiveVoiceProviderUseCase,
        override val deleteLiveVoiceProvider: DeleteLiveVoiceProviderUseCase,
    ) : SettingsLiveVoiceUseCases

class SettingsMediaUseCasesImpl
    @Inject
    constructor(
        override val getMediaProviders: GetMediaProvidersUseCase,
        override val saveMediaProvider: SaveMediaProviderUseCase,
        override val deleteMediaProvider: DeleteMediaProviderUseCase,
    ) : SettingsMediaUseCases

class SettingsAuthUseCasesImpl
    @Inject
    constructor(
        override val loginWithGoogle: LoginWithGoogleUseCase,
        override val logout: LogoutUseCase,
        override val getAuthState: GetAuthStateUseCase,
    ) : SettingsAuthUseCases

class SettingsDataControlsUseCasesImpl
    @Inject
    constructor(
        override val deleteAllChats: DeleteAllChatsUseCase,
        override val deleteAllMedia: DeleteAllMediaUseCase,
        override val deleteAllMemories: DeleteAllMemoriesUseCase,
        override val deleteAllArtifacts: DeleteAllArtifactsUseCase,
        override val deleteAccount: DeleteAccountUseCase,
    ) : SettingsDataControlsUseCases

class SettingsMemoriesUseCasesImpl
    @Inject
    constructor(
        private val memoriesRepository: com.browntowndev.enclave.domain.port.repository.MemoriesRepository,
    ) : SettingsMemoriesUseCases {
        override fun getAllMemoriesFlow() = memoriesRepository.getAllMemoriesFlow()

        override suspend fun insertMemory(
            category: com.browntowndev.enclave.domain.model.memory.MemoryCategory,
            content: String,
        ) = memoriesRepository.insertMemory(category, content)

        override suspend fun updateMemory(
            id: String,
            content: String,
            category: com.browntowndev.enclave.domain.model.memory.MemoryCategory?,
        ) = memoriesRepository.updateMemory(id, content, category)

        override suspend fun deleteMemory(id: String) = memoriesRepository.deleteMemory(id)
    }

object EmptySettingsLiveVoiceUseCases : SettingsLiveVoiceUseCases {
    private val repository =
        object : LiveVoiceProviderRepositoryPort {
            override fun getLiveVoiceProviders(): Flow<List<LiveVoiceProviderAsset>> = flowOf(emptyList())

            override suspend fun getLiveVoiceProvidersSync(): List<LiveVoiceProviderAsset> = emptyList()

            override suspend fun getLiveVoiceProvider(id: LiveVoiceProviderId): LiveVoiceProviderAsset? = null

            override suspend fun saveLiveVoiceProvider(
                asset: LiveVoiceProviderAsset,
                apiKey: String?,
            ): LiveVoiceProviderId = asset.id

            override suspend fun deleteLiveVoiceProvider(id: LiveVoiceProviderId) = Unit
        }

    override val getLiveVoiceProviders = GetLiveVoiceProvidersUseCase(repository)
    override val saveLiveVoiceProvider = SaveLiveVoiceProviderUseCase(repository)
    override val deleteLiveVoiceProvider = DeleteLiveVoiceProviderUseCase(repository)
}

class SettingsUseCasesImpl
    @Inject
    constructor(
        override val preferences: SettingsPreferencesUseCases,
        override val localModels: SettingsLocalModelUseCases,
        override val apiProviders: SettingsApiProviderUseCases,
        override val assignments: SettingsAssignmentUseCases,
        override val deletion: SettingsDeletionUseCases,
        override val tts: SettingsTtsUseCases,
        override val liveVoice: SettingsLiveVoiceUseCases,
        override val media: SettingsMediaUseCases,
        override val auth: SettingsAuthUseCases,
        override val memories: SettingsMemoriesUseCases,
        override val dataControls: SettingsDataControlsUseCases,
    ) : SettingsUseCases {
        constructor(
            preferences: SettingsPreferencesUseCases,
            localModels: SettingsLocalModelUseCases,
            apiProviders: SettingsApiProviderUseCases,
            assignments: SettingsAssignmentUseCases,
            deletion: SettingsDeletionUseCases,
            tts: SettingsTtsUseCases,
            media: SettingsMediaUseCases,
            auth: SettingsAuthUseCases,
            memories: SettingsMemoriesUseCases,
            dataControls: SettingsDataControlsUseCases,
        ) : this(
            preferences = preferences,
            localModels = localModels,
            apiProviders = apiProviders,
            assignments = assignments,
            deletion = deletion,
            tts = tts,
            liveVoice = EmptySettingsLiveVoiceUseCases,
            media = media,
            auth = auth,
            memories = memories,
            dataControls = dataControls,
        )
    }
