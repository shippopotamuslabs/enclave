package com.browntowndev.enclave.feature.settings

import com.browntowndev.enclave.core.testing.FakeApiKeyProvider
import com.browntowndev.enclave.core.testing.FakeApiModelRepository
import com.browntowndev.enclave.core.testing.FakeAuthManager
import com.browntowndev.enclave.core.testing.FakeChatRepository
import com.browntowndev.enclave.core.testing.FakeCleanupRepository
import com.browntowndev.enclave.core.testing.FakeCloudSyncPort
import com.browntowndev.enclave.core.testing.FakeCryptoManagerPort
import com.browntowndev.enclave.core.testing.FakeDefaultModelRepository
import com.browntowndev.enclave.core.testing.FakeDownloadWorkSchedulerPort
import com.browntowndev.enclave.core.testing.FakeFetchApiProviderModelDetailUseCase
import com.browntowndev.enclave.core.testing.FakeFetchApiProviderModelsUseCase
import com.browntowndev.enclave.core.testing.FakeGpuCapability
import com.browntowndev.enclave.core.testing.FakeInferenceTelemetryRepositoryPort
import com.browntowndev.enclave.core.testing.FakeLocalImageGenerationCapability
import com.browntowndev.enclave.core.testing.FakeLocalModelRepository
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.core.testing.FakeMediaProviderRepository
import com.browntowndev.enclave.core.testing.FakeMemoriesRepository
import com.browntowndev.enclave.core.testing.FakeModelConfigFetcher
import com.browntowndev.enclave.core.testing.FakeModelFileScanner
import com.browntowndev.enclave.core.testing.FakeModelPackVerifierPort
import com.browntowndev.enclave.core.testing.FakeSettingsRepository
import com.browntowndev.enclave.core.testing.FakeStudioRepository
import com.browntowndev.enclave.core.testing.FakeTransactionProvider
import com.browntowndev.enclave.core.testing.FakeTtsProviderRepository
import com.browntowndev.enclave.core.testing.FakeUtilityModelRepository
import com.browntowndev.enclave.core.testing.FakeVaultMetadataPort
import com.browntowndev.enclave.core.testing.TestClock
import com.browntowndev.enclave.core.ui.error.ViewModelErrorHandler
import com.browntowndev.enclave.domain.model.config.ApiCredentialsId
import com.browntowndev.enclave.domain.model.config.ApiModelAsset
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.config.DefaultModelAssignment
import com.browntowndev.enclave.domain.model.config.LiveVoiceProviderAsset
import com.browntowndev.enclave.domain.model.config.LiveVoiceProviderId
import com.browntowndev.enclave.domain.model.config.LocalModelAsset
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelId
import com.browntowndev.enclave.domain.model.config.MediaProviderAsset
import com.browntowndev.enclave.domain.model.config.MediaProviderId
import com.browntowndev.enclave.domain.model.config.TtsProviderAsset
import com.browntowndev.enclave.domain.model.config.TtsProviderId
import com.browntowndev.enclave.domain.model.config.UtilityModelMetadata
import com.browntowndev.enclave.domain.model.download.ScheduledDownload
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.ApiReasoningEffort
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.settings.AppTheme
import com.browntowndev.enclave.domain.model.settings.SearchProvider
import com.browntowndev.enclave.domain.model.settings.SystemPromptOption
import com.browntowndev.enclave.domain.model.telemetry.UsagePeriod
import com.browntowndev.enclave.domain.model.telemetry.UsageSpendSummary
import com.browntowndev.enclave.domain.port.auth.AuthState
import com.browntowndev.enclave.domain.port.auth.UserProfile
import com.browntowndev.enclave.domain.port.repository.SettingsData
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
import com.browntowndev.enclave.domain.usecase.modelconfig.GetLocalModelAssetsUseCase
import com.browntowndev.enclave.domain.usecase.modelconfig.GetModelPacksForSettingsUseCase
import com.browntowndev.enclave.domain.usecase.modelconfig.ModelPackSettingsRow
import com.browntowndev.enclave.domain.usecase.modelconfig.ObserveActiveUtilityModelsUseCase
import com.browntowndev.enclave.domain.usecase.modelconfig.ReDownloadModelPackUseCase
import com.browntowndev.enclave.domain.usecase.modelconfig.ReDownloadModelUseCase
import com.browntowndev.enclave.domain.usecase.security.AdoptCloudVaultUseCase
import com.browntowndev.enclave.domain.usecase.security.BootstrapCloudVaultFromLocalUseCase
import com.browntowndev.enclave.domain.usecase.security.CreateVaultUseCase
import com.browntowndev.enclave.domain.usecase.security.DetectVaultReconciliationStateUseCase
import com.browntowndev.enclave.domain.usecase.security.StartCloudSyncListeningUseCase
import com.browntowndev.enclave.domain.usecase.security.StartCloudSyncResult
import com.browntowndev.enclave.domain.usecase.security.UnlockVaultFromStoredMetadataUseCase
import com.browntowndev.enclave.domain.usecase.security.UnlockVaultUseCase
import com.browntowndev.enclave.domain.model.config.OpenRouterModelEndpoint
import com.browntowndev.enclave.domain.usecase.settings.ApiModelDiscoveryRequest
import com.browntowndev.enclave.domain.usecase.settings.ApiModelDiscoveryResult
import com.browntowndev.enclave.domain.usecase.settings.ApiProviderDraft
import com.browntowndev.enclave.domain.usecase.settings.ApiProviderDraftSaveResult
import com.browntowndev.enclave.domain.usecase.settings.ApplyApiModelMetadataDefaultsUseCase
import com.browntowndev.enclave.domain.usecase.settings.DeleteLiveVoiceProviderUseCase
import com.browntowndev.enclave.domain.usecase.settings.DeleteTtsProviderUseCase
import com.browntowndev.enclave.domain.usecase.settings.GetLiveVoiceProvidersUseCase
import com.browntowndev.enclave.domain.usecase.settings.GetRestorableLocalModelsUseCase
import com.browntowndev.enclave.domain.usecase.settings.GetTtsProvidersUseCase
import com.browntowndev.enclave.domain.usecase.settings.LocalModelPresetDraft
import com.browntowndev.enclave.domain.usecase.settings.SaveLiveVoiceProviderUseCase
import com.browntowndev.enclave.domain.usecase.settings.SaveLocalModelPresetUseCase
import com.browntowndev.enclave.domain.usecase.settings.SaveTtsProviderUseCase
import com.browntowndev.enclave.domain.usecase.settings.TtsProviderDraft
import com.browntowndev.enclave.domain.usecase.settings.ClearExaApiKeyUseCase
import com.browntowndev.enclave.domain.usecase.settings.ClearTavilyApiKeyUseCase
import com.browntowndev.enclave.domain.usecase.settings.ClearXApiKeyUseCase
import com.browntowndev.enclave.domain.usecase.settings.DeleteAccountUseCase
import com.browntowndev.enclave.domain.usecase.settings.DeleteAllArtifactsUseCase
import com.browntowndev.enclave.domain.usecase.settings.DeleteAllChatsUseCase
import com.browntowndev.enclave.domain.usecase.settings.DeleteAllMediaUseCase
import com.browntowndev.enclave.domain.usecase.settings.DeleteAllMemoriesUseCase
import com.browntowndev.enclave.domain.usecase.settings.DiscoverApiModelsUseCase
import com.browntowndev.enclave.domain.usecase.settings.ExecuteModelDeletionWithReassignmentUseCase
import com.browntowndev.enclave.domain.usecase.settings.GetSettingsUseCase
import com.browntowndev.enclave.domain.usecase.settings.ModelDeletionTarget
import com.browntowndev.enclave.domain.usecase.settings.PrepareModelDeletionUseCase
import com.browntowndev.enclave.domain.usecase.settings.PreparedModelDeletion
import com.browntowndev.enclave.domain.usecase.settings.ResolveAssignedModelSelectionUseCase
import com.browntowndev.enclave.domain.usecase.settings.ResolvedAssignedModelSelection
import com.browntowndev.enclave.domain.usecase.settings.SaveApiPresetUseCase
import com.browntowndev.enclave.domain.usecase.settings.SaveApiProviderDraftUseCase
import com.browntowndev.enclave.domain.usecase.settings.SaveExaApiKeyUseCase
import com.browntowndev.enclave.domain.usecase.settings.SaveTavilyApiKeyUseCase
import com.browntowndev.enclave.domain.usecase.settings.SaveXApiKeyUseCase
import com.browntowndev.enclave.domain.usecase.settings.TestApiProviderConnectionUseCase
import com.browntowndev.enclave.domain.usecase.settings.UpdateAllowMemoriesUseCase
import com.browntowndev.enclave.domain.usecase.settings.UpdateAutomaticErrorTrackingEnabledUseCase
import com.browntowndev.enclave.domain.usecase.settings.UpdateBackgroundInferenceEnabledUseCase
import com.browntowndev.enclave.domain.usecase.settings.UpdateCustomPromptTextUseCase
import com.browntowndev.enclave.domain.usecase.settings.UpdateCustomizationEnabledUseCase
import com.browntowndev.enclave.domain.usecase.settings.UpdateHapticResponseUseCase
import com.browntowndev.enclave.domain.usecase.settings.UpdateSearchEnabledUseCase
import com.browntowndev.enclave.domain.usecase.settings.UpdateSearchProviderUseCase
import com.browntowndev.enclave.domain.usecase.settings.UpdateSelectedPromptOptionUseCase
import com.browntowndev.enclave.domain.usecase.settings.UpdateShowSearchProviderSwitchAlertUseCase
import com.browntowndev.enclave.domain.usecase.settings.UpdateThemeUseCase
import com.browntowndev.enclave.domain.usecase.settings.UpdateXSearchEnabledUseCase
import com.browntowndev.enclave.domain.usecase.telemetry.ObserveUsageSpendSummaryUseCase
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Recording [ViewModelErrorHandler] fake for settings tests (ENC-28): the
 * coroutine exception handler records throwables instead of crashing the test.
 */
class FakeViewModelErrorHandler : ViewModelErrorHandler {
    private val _errorEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    override val errorEvents: SharedFlow<String> = _errorEvents.asSharedFlow()

    val handledThrowables = mutableListOf<Throwable>()
    val handledMessages = mutableListOf<String>()
    val handledTags = mutableListOf<String>()
    val handledUserMessages = mutableListOf<String>()

    override fun handleError(tag: String, message: String, throwable: Throwable, userMessage: String) {
        handledThrowables += throwable
        handledMessages += message
        handledTags += tag
        handledUserMessages += userMessage
        _errorEvents.tryEmit(userMessage)
    }

    override fun coroutineExceptionHandler(
        tag: String,
        message: String,
        userMessage: String,
    ): CoroutineExceptionHandler =
        CoroutineExceptionHandler { _, throwable ->
            handledThrowables += throwable
            handledMessages += message
            _errorEvents.tryEmit(userMessage)
        }
}

/** Flow-backed [GetSettingsUseCase] fake (ENC-28). */
class FakeGetSettingsUseCase : GetSettingsUseCase(FakeSettingsRepository()) {
    var flow: Flow<SettingsData> = MutableStateFlow(SettingsData())

    override fun invoke(): Flow<SettingsData> = flow
}

/** Flow-backed [GetLocalModelAssetsUseCase] fake (ENC-28). */
class FakeGetLocalModelAssetsUseCase : GetLocalModelAssetsUseCase {
    var flow: Flow<List<LocalModelAsset>> = MutableStateFlow(emptyList())
    var softDeleted: List<LocalModelAsset> = emptyList()

    override fun invoke(): Flow<List<LocalModelAsset>> = flow

    override suspend fun getSoftDeletedModels(): List<LocalModelAsset> = softDeleted
}

/** Result-backed [GetRestorableLocalModelsUseCase] fake (ENC-28). */
class FakeGetRestorableLocalModelsUseCase :
    GetRestorableLocalModelsUseCase(com.browntowndev.enclave.domain.usecase.modelconfig.GetLocalModelAssetsUseCaseImpl(FakeLocalModelRepository())) {
    var result: List<LocalModelAsset> = emptyList()

    override suspend fun invoke(): List<LocalModelAsset> = result
}

/** Flow-backed [GetApiModelAssetsUseCase] fake (ENC-28). */
class FakeGetApiModelAssetsUseCase : GetApiModelAssetsUseCase {
    var flow: Flow<List<ApiModelAsset>> = MutableStateFlow(emptyList())

    override fun invoke(): Flow<List<ApiModelAsset>> = flow
}

/** Flow-backed [GetDefaultModelsUseCase] fake (ENC-28). */
class FakeGetDefaultModelsUseCase : GetDefaultModelsUseCase {
    var flow: Flow<List<DefaultModelAssignment>> = MutableStateFlow(emptyList())

    override fun invoke(): Flow<List<DefaultModelAssignment>> = flow
}

/** Flow-backed [GetTtsProvidersUseCase] fake (ENC-28). */
class FakeGetTtsProvidersUseCase : GetTtsProvidersUseCase(FakeTtsProviderRepository()) {
    var flow: Flow<List<TtsProviderAsset>> = MutableStateFlow(emptyList())

    override fun invoke(): Flow<List<TtsProviderAsset>> = flow
}

/** Flow-backed [GetMediaProvidersUseCase] fake (ENC-28). */
class FakeGetMediaProvidersUseCase : GetMediaProvidersUseCase(FakeMediaProviderRepository()) {
    var flow: Flow<List<MediaProviderAsset>> = MutableStateFlow(emptyList())

    override fun invoke(): Flow<List<MediaProviderAsset>> = flow
}

/** Flow-backed [GetLiveVoiceProvidersUseCase] fake (ENC-28). */
class FakeGetLiveVoiceProvidersUseCase :
    GetLiveVoiceProvidersUseCase(com.browntowndev.enclave.core.testing.FakeLiveVoiceProviderRepository()) {
    var flow: Flow<List<LiveVoiceProviderAsset>> = MutableStateFlow(emptyList())

    override fun invoke(): Flow<List<LiveVoiceProviderAsset>> = flow
}

/** StateFlow-backed [GetAuthStateUseCase] fake (ENC-28). */
class FakeGetAuthStateUseCase : GetAuthStateUseCase(FakeAuthManager()) {
    val authState = MutableStateFlow<AuthState>(AuthState.Loading)

    override fun invoke(): MutableStateFlow<AuthState> = authState
}

/** Flow-backed [ObserveActiveUtilityModelsUseCase] fake (ENC-28). */
class FakeObserveActiveUtilityModelsUseCase : ObserveActiveUtilityModelsUseCase(FakeUtilityModelRepository()) {
    var flow: Flow<List<UtilityModelMetadata>> = MutableStateFlow(emptyList())

    override fun invoke(): Flow<List<UtilityModelMetadata>> = flow
}

/** Flow-backed [ObserveUsageSpendSummaryUseCase] fake (ENC-28). */
class FakeObserveUsageSpendSummaryUseCase : ObserveUsageSpendSummaryUseCase(FakeInferenceTelemetryRepositoryPort()) {
    var flowFactory: (UsagePeriod) -> Flow<UsageSpendSummary> = { MutableStateFlow(nullUsageSpendSummary(it)) }

    override fun invoke(period: UsagePeriod): Flow<UsageSpendSummary> = flowFactory(period)
}

private fun nullUsageSpendSummary(period: UsagePeriod): UsageSpendSummary =
    UsageSpendSummary(
        period = period,
        periodStartUtcMs = null,
        periodEndUtcMs = null,
        totalCostUsd = java.math.BigDecimal.ZERO,
        telemetryBackedTurnCount = 0,
        missingCostTurnCount = 0,
        providerBreakdown = emptyList(),
    )

/** Recording [UpdateThemeUseCase] fake (ENC-28). */
class FakeUpdateThemeUseCase : UpdateThemeUseCase(FakeSettingsRepository()) {
    val calls = mutableListOf<AppTheme>()

    override suspend fun invoke(theme: AppTheme) {
        calls += theme
    }
}

/** Recording [UpdateHapticResponseUseCase] fake (ENC-28). */
class FakeUpdateHapticResponseUseCase : UpdateHapticResponseUseCase(FakeSettingsRepository()) {
    val calls = mutableListOf<Boolean>()

    override suspend fun invoke(enabled: Boolean) {
        calls += enabled
    }
}

/** Recording [UpdateCustomizationEnabledUseCase] fake (ENC-28). */
class FakeUpdateCustomizationEnabledUseCase : UpdateCustomizationEnabledUseCase(FakeSettingsRepository()) {
    val calls = mutableListOf<Boolean>()

    override suspend fun invoke(enabled: Boolean) {
        calls += enabled
    }
}

/** Recording [UpdateSelectedPromptOptionUseCase] fake (ENC-28). */
class FakeUpdateSelectedPromptOptionUseCase : UpdateSelectedPromptOptionUseCase(FakeSettingsRepository()) {
    val calls = mutableListOf<SystemPromptOption>()

    override suspend fun invoke(option: SystemPromptOption) {
        calls += option
    }
}

/** Recording [UpdateCustomPromptTextUseCase] fake (ENC-28). */
class FakeUpdateCustomPromptTextUseCase : UpdateCustomPromptTextUseCase(FakeSettingsRepository()) {
    val calls = mutableListOf<String>()

    override suspend fun invoke(text: String) {
        calls += text
    }
}

/** Recording [UpdateAllowMemoriesUseCase] fake (ENC-28). */
class FakeUpdateAllowMemoriesUseCase : UpdateAllowMemoriesUseCase(FakeSettingsRepository()) {
    val calls = mutableListOf<Boolean>()

    override suspend fun invoke(allowed: Boolean) {
        calls += allowed
    }
}

/** Recording [UpdateSearchEnabledUseCase] fake (ENC-28). */
class FakeUpdateSearchEnabledUseCase : UpdateSearchEnabledUseCase(FakeSettingsRepository()) {
    val calls = mutableListOf<Boolean>()

    override suspend fun invoke(enabled: Boolean) {
        calls += enabled
    }
}

/** Recording [UpdateSearchProviderUseCase] fake (ENC-28). */
class FakeUpdateSearchProviderUseCase : UpdateSearchProviderUseCase(FakeSettingsRepository()) {
    val calls = mutableListOf<SearchProvider>()

    override suspend fun invoke(provider: SearchProvider) {
        calls += provider
    }
}

/** Recording [SaveTavilyApiKeyUseCase] fake (ENC-28). */
class FakeSaveTavilyApiKeyUseCase : SaveTavilyApiKeyUseCase(FakeSettingsRepository()) {
    val calls = mutableListOf<String>()

    override suspend fun invoke(apiKey: String) {
        calls += apiKey
    }
}

/** Recording [ClearTavilyApiKeyUseCase] fake (ENC-28). */
class FakeClearTavilyApiKeyUseCase : ClearTavilyApiKeyUseCase(FakeSettingsRepository()) {
    var callCount = 0

    override suspend fun invoke() {
        callCount++
    }
}

/** Recording [SaveExaApiKeyUseCase] fake (ENC-28). */
class FakeSaveExaApiKeyUseCase : SaveExaApiKeyUseCase(FakeSettingsRepository()) {
    val calls = mutableListOf<String>()

    override suspend fun invoke(apiKey: String) {
        calls += apiKey
    }
}

/** Recording [ClearExaApiKeyUseCase] fake (ENC-28). */
class FakeClearExaApiKeyUseCase : ClearExaApiKeyUseCase(FakeSettingsRepository()) {
    var callCount = 0

    override suspend fun invoke() {
        callCount++
    }
}

/** Recording [UpdateXSearchEnabledUseCase] fake (ENC-28). */
class FakeUpdateXSearchEnabledUseCase : UpdateXSearchEnabledUseCase(FakeSettingsRepository()) {
    val calls = mutableListOf<Boolean>()

    override suspend fun invoke(enabled: Boolean) {
        calls += enabled
    }
}

/** Recording [SaveXApiKeyUseCase] fake (ENC-28). */
class FakeSaveXApiKeyUseCase : SaveXApiKeyUseCase(FakeSettingsRepository()) {
    val calls = mutableListOf<String>()

    override suspend fun invoke(apiKey: String) {
        calls += apiKey
    }
}

/** Recording [ClearXApiKeyUseCase] fake (ENC-28). */
class FakeClearXApiKeyUseCase : ClearXApiKeyUseCase(FakeSettingsRepository()) {
    var callCount = 0

    override suspend fun invoke() {
        callCount++
    }
}

/** Recording [UpdateShowSearchProviderSwitchAlertUseCase] fake (ENC-28). */
class FakeUpdateShowSearchProviderSwitchAlertUseCase : UpdateShowSearchProviderSwitchAlertUseCase(FakeSettingsRepository()) {
    val calls = mutableListOf<Boolean>()

    override suspend fun invoke(show: Boolean) {
        calls += show
    }
}

/** Recording [UpdateBackgroundInferenceEnabledUseCase] fake (ENC-28). */
class FakeUpdateBackgroundInferenceEnabledUseCase : UpdateBackgroundInferenceEnabledUseCase(FakeSettingsRepository()) {
    val calls = mutableListOf<Boolean>()

    override suspend fun invoke(enabled: Boolean) {
        calls += enabled
    }
}

/** Recording [UpdateAutomaticErrorTrackingEnabledUseCase] fake (ENC-28). */
class FakeUpdateAutomaticErrorTrackingEnabledUseCase : UpdateAutomaticErrorTrackingEnabledUseCase(FakeSettingsRepository()) {
    val calls = mutableListOf<Boolean>()

    override suspend fun invoke(enabled: Boolean) {
        calls += enabled
    }
}

/** Recording [SaveLocalModelPresetUseCase] fake (ENC-28). */
class FakeSaveLocalModelPresetUseCase :
    SaveLocalModelPresetUseCase(
        com.browntowndev.enclave.domain.usecase.modelconfig.SaveLocalModelConfigurationUseCaseImpl(FakeLocalModelRepository()),
    ) {
    val calls = mutableListOf<Pair<LocalModelId, LocalModelPresetDraft>>()
    var result: Result<LocalModelConfigurationId> = Result.success(LocalModelConfigurationId("preset-1"))

    override suspend fun invoke(localModelId: LocalModelId, draft: LocalModelPresetDraft): Result<LocalModelConfigurationId> {
        calls += localModelId to draft
        return result
    }
}

/** Recording [ReDownloadModelUseCase] fake (ENC-28). */
class FakeReDownloadModelUseCase :
    ReDownloadModelUseCase(FakeLocalModelRepository(), FakeDownloadWorkSchedulerPort(), FakeLoggingPort()) {
    val calls = mutableListOf<LocalModelId>()
    var result: Result<ScheduledDownload> =
        Result.failure(IllegalStateException("FakeReDownloadModelUseCase.result not set"))

    override suspend fun invoke(modelId: LocalModelId): Result<ScheduledDownload> {
        calls += modelId
        return result
    }
}

/** Recording [ReDownloadModelPackUseCase] fake (ENC-28). */
class FakeReDownloadModelPackUseCase :
    ReDownloadModelPackUseCase(
        FakeDownloadWorkSchedulerPort(),
        com.browntowndev.enclave.domain.usecase.download.VerifyModelPackCompletenessUseCase(
            FakeModelPackVerifierPort(),
            com.browntowndev.enclave.domain.usecase.download.ResolveModelCatalogUseCase(FakeModelConfigFetcher()),
        ),
        com.browntowndev.enclave.domain.usecase.download.ResolveModelCatalogUseCase(FakeModelConfigFetcher()),
    ) {
    val calls = mutableListOf<String>()
    var result: Result<List<ScheduledDownload>> = Result.success(emptyList())

    override suspend fun invoke(packId: String): Result<List<ScheduledDownload>> {
        calls += packId
        return result
    }
}

/** Result-backed [GetModelPacksForSettingsUseCase] fake (ENC-28). */
class FakeGetModelPacksForSettingsUseCase :
    GetModelPacksForSettingsUseCase(
        com.browntowndev.enclave.domain.usecase.download.ResolveModelCatalogUseCase(FakeModelConfigFetcher()),
        com.browntowndev.enclave.domain.usecase.download.VerifyModelPackCompletenessUseCase(
            FakeModelPackVerifierPort(),
            com.browntowndev.enclave.domain.usecase.download.ResolveModelCatalogUseCase(FakeModelConfigFetcher()),
        ),
        FakeModelPackVerifierPort(),
        FakeLocalModelRepository(),
        FakeUtilityModelRepository(),
        FakeLocalImageGenerationCapability(),
    ) {
    var result: List<ModelPackSettingsRow> = emptyList()

    override suspend fun invoke(): List<ModelPackSettingsRow> = result
}

/** Recording [SaveApiProviderDraftUseCase] fake (ENC-28). */
class FakeSaveApiProviderDraftUseCase :
    SaveApiProviderDraftUseCase(
        com.browntowndev.enclave.domain.usecase.byok.SaveApiCredentialsUseCaseImpl(FakeApiModelRepository()),
        com.browntowndev.enclave.domain.usecase.byok.SaveApiModelConfigurationUseCaseImpl(FakeApiModelRepository()),
        com.browntowndev.enclave.domain.usecase.byok.GetApiModelAssetsUseCaseImpl(FakeApiModelRepository()),
        FakeApiModelRepository(),
    ) {
    val calls = mutableListOf<ApiProviderDraft>()
    var result: Result<ApiProviderDraftSaveResult> =
        Result.failure(IllegalStateException("FakeSaveApiProviderDraftUseCase.result not set"))

    override suspend fun invoke(draft: ApiProviderDraft): Result<ApiProviderDraftSaveResult> {
        calls += draft
        return result
    }
}

/** Recording [SaveApiPresetUseCase] fake (ENC-28). */
class FakeSaveApiPresetUseCase :
    SaveApiPresetUseCase(
        com.browntowndev.enclave.domain.usecase.byok.SaveApiModelConfigurationUseCaseImpl(FakeApiModelRepository()),
    ) {
    data class Args(
        val provider: ApiProvider,
        val parentCredentialsId: ApiCredentialsId,
        val defaultReasoningEffort: ApiReasoningEffort?,
        val draft: com.browntowndev.enclave.domain.usecase.settings.ApiPresetDraft,
    )

    val calls = mutableListOf<Args>()
    var result: Result<ApiModelConfigurationId> = Result.success(ApiModelConfigurationId("preset-1"))

    override suspend fun invoke(
        provider: ApiProvider,
        parentCredentialsId: ApiCredentialsId,
        defaultReasoningEffort: ApiReasoningEffort?,
        draft: com.browntowndev.enclave.domain.usecase.settings.ApiPresetDraft,
    ): Result<ApiModelConfigurationId> {
        calls += Args(provider, parentCredentialsId, defaultReasoningEffort, draft)
        return result
    }
}

/** Recording [DiscoverApiModelsUseCase] fake (ENC-28). */
class FakeDiscoverApiModelsUseCase :
    DiscoverApiModelsUseCase(FakeFetchApiProviderModelsUseCase(), FakeFetchApiProviderModelDetailUseCase()) {
    val calls = mutableListOf<ApiModelDiscoveryRequest>()
    var result: ApiModelDiscoveryResult =
        ApiModelDiscoveryResult(
            models = emptyList(),
            scope = com.browntowndev.enclave.domain.usecase.settings.ApiModelDiscoveryScope(ApiProvider.OPENAI, "", null),
        )

    override suspend fun invoke(request: ApiModelDiscoveryRequest): ApiModelDiscoveryResult {
        calls += request
        return result
    }
}

/** Recording [TestApiProviderConnectionUseCase] fake (ENC-28). */
class FakeTestApiProviderConnectionUseCase : TestApiProviderConnectionUseCase(FakeFetchApiProviderModelsUseCase()) {
    data class Args(
        val provider: ApiProvider,
        val currentApiKey: String,
        val credentialAlias: String?,
        val baseUrl: String?,
    )

    val calls = mutableListOf<Args>()
    var result: Result<Unit> = Result.success(Unit)

    override suspend fun invoke(
        provider: ApiProvider,
        currentApiKey: String,
        credentialAlias: String?,
        baseUrl: String?,
    ): Result<Unit> {
        calls += Args(provider, currentApiKey, credentialAlias, baseUrl)
        return result
    }
}

/** Recording [FetchOpenRouterModelEndpointsUseCase] fake (ENC-28). */
class FakeFetchOpenRouterModelEndpointsUseCase : FetchOpenRouterModelEndpointsUseCase {
    val calls = mutableListOf<String>()
    var result: List<OpenRouterModelEndpoint> = emptyList()

    /** When set, invoke throws this instead of returning [result]. */
    var failure: Throwable? = null

    override suspend fun invoke(
        modelId: String,
        currentApiKey: String,
        credentialAlias: String?,
        baseUrl: String?,
    ): List<OpenRouterModelEndpoint> {
        calls += modelId
        failure?.let { throw it }
        return result
    }
}

/** Recording [SetDefaultModelUseCase] fake (ENC-28). */
class FakeSetDefaultModelUseCase : SetDefaultModelUseCase {
    data class Args(
        val modelType: ModelType,
        val localConfigId: LocalModelConfigurationId?,
        val apiConfigId: ApiModelConfigurationId?,
        val ttsProviderId: TtsProviderId?,
        val mediaProviderId: MediaProviderId?,
        val liveVoiceProviderId: LiveVoiceProviderId?,
    )

    val calls = mutableListOf<Args>()

    override suspend fun invoke(
        modelType: ModelType,
        localConfigId: LocalModelConfigurationId?,
        apiConfigId: ApiModelConfigurationId?,
        ttsProviderId: TtsProviderId?,
        mediaProviderId: MediaProviderId?,
        liveVoiceProviderId: LiveVoiceProviderId?,
    ) {
        calls += Args(modelType, localConfigId, apiConfigId, ttsProviderId, mediaProviderId, liveVoiceProviderId)
    }
}

/** Recording [ClearDefaultModelUseCase] fake (ENC-28). */
class FakeClearDefaultModelUseCase : ClearDefaultModelUseCase(FakeDefaultModelRepository()) {
    val calls = mutableListOf<ModelType>()

    /** Optional hook run inside invoke (cross-fake ordering assertions). */
    var onInvoke: (() -> Unit)? = null

    override suspend fun invoke(modelType: ModelType) {
        calls += modelType
        onInvoke?.invoke()
    }
}

/** Result-backed [ResolveAssignedModelSelectionUseCase] fake (ENC-28). */
class FakeResolveAssignedModelSelectionUseCase :
    ResolveAssignedModelSelectionUseCase(
        com.browntowndev.enclave.domain.usecase.byok.GetDefaultModelsUseCaseImpl(FakeDefaultModelRepository()),
        com.browntowndev.enclave.domain.usecase.modelconfig.GetLocalModelAssetsUseCaseImpl(FakeLocalModelRepository()),
        com.browntowndev.enclave.domain.usecase.byok.GetApiModelAssetsUseCaseImpl(FakeApiModelRepository()),
        GetTtsProvidersUseCase(FakeTtsProviderRepository()),
        GetLiveVoiceProvidersUseCase(com.browntowndev.enclave.core.testing.FakeLiveVoiceProviderRepository()),
    ) {
    var result: ResolvedAssignedModelSelection? = null
    val calls = mutableListOf<ModelType>()

    override suspend fun invoke(modelType: ModelType): ResolvedAssignedModelSelection? {
        calls += modelType
        return result
    }
}

/** Result-backed [PrepareModelDeletionUseCase] fake (ENC-28). */
class FakePrepareModelDeletionUseCase :
    PrepareModelDeletionUseCase(
        com.browntowndev.enclave.domain.usecase.byok.GetDefaultModelsUseCaseImpl(FakeDefaultModelRepository()),
        com.browntowndev.enclave.domain.usecase.modelconfig.GetLocalModelAssetsUseCaseImpl(FakeLocalModelRepository()),
        com.browntowndev.enclave.domain.usecase.byok.GetApiModelAssetsUseCaseImpl(FakeApiModelRepository()),
        com.browntowndev.enclave.domain.usecase.modelconfig.DeleteLocalModelUseCase(
            FakeLocalModelRepository(),
            FakeDefaultModelRepository(),
            FakeModelFileScanner(),
            FakeApiModelRepository(),
            FakeLoggingPort(),
        ),
        com.browntowndev.enclave.domain.usecase.byok.DeleteApiCredentialsUseCaseImpl(
            FakeApiModelRepository(),
            FakeLocalModelRepository(),
            FakeDefaultModelRepository(),
            FakeTransactionProvider(),
        ),
        com.browntowndev.enclave.domain.usecase.byok.DeleteApiModelConfigurationUseCaseImpl(
            FakeApiModelRepository(),
            FakeDefaultModelRepository(),
            FakeTransactionProvider(),
        ),
        com.browntowndev.enclave.domain.usecase.download.ResolveModelCatalogUseCase(FakeModelConfigFetcher()),
        GetMediaProvidersUseCase(FakeMediaProviderRepository()),
    ) {
    var result: PreparedModelDeletion? = null
    val calls = mutableListOf<ModelDeletionTarget>()

    override suspend fun invoke(target: ModelDeletionTarget): PreparedModelDeletion {
        calls += target
        return result ?: error("FakePrepareModelDeletionUseCase.result not set")
    }
}

/** Recording [ExecuteModelDeletionWithReassignmentUseCase] fake (ENC-28). */
class FakeExecuteModelDeletionWithReassignmentUseCase :
    ExecuteModelDeletionWithReassignmentUseCase(
        com.browntowndev.enclave.domain.usecase.modelconfig.DeleteLocalModelUseCase(
            FakeLocalModelRepository(),
            FakeDefaultModelRepository(),
            FakeModelFileScanner(),
            FakeApiModelRepository(),
            FakeLoggingPort(),
        ),
        com.browntowndev.enclave.domain.usecase.modelconfig.DeleteLocalModelConfigurationUseCaseImpl(FakeLocalModelRepository()),
        com.browntowndev.enclave.domain.usecase.byok.DeleteApiCredentialsUseCaseImpl(
            FakeApiModelRepository(),
            FakeLocalModelRepository(),
            FakeDefaultModelRepository(),
            FakeTransactionProvider(),
        ),
        com.browntowndev.enclave.domain.usecase.byok.DeleteApiModelConfigurationUseCaseImpl(
            FakeApiModelRepository(),
            FakeDefaultModelRepository(),
            FakeTransactionProvider(),
        ),
        FakeSetDefaultModelUseCase(),
        com.browntowndev.enclave.domain.usecase.modelconfig.DeleteModelPackUseCase(
            com.browntowndev.enclave.domain.usecase.modelconfig.DeleteLocalModelUseCase(
                FakeLocalModelRepository(),
                FakeDefaultModelRepository(),
                FakeModelFileScanner(),
                FakeApiModelRepository(),
                FakeLoggingPort(),
            ),
            FakeUtilityModelRepository(),
            FakeModelFileScanner(),
            com.browntowndev.enclave.domain.usecase.download.ResolveModelCatalogUseCase(FakeModelConfigFetcher()),
        ),
    ) {
    data class Args(
        val target: ModelDeletionTarget,
        val modelTypesNeedingReassignment: List<ModelType>,
        val replacementLocalConfigId: LocalModelConfigurationId?,
        val replacementApiConfigId: ApiModelConfigurationId?,
        val replacementMediaProviderId: MediaProviderId?,
    )

    val calls = mutableListOf<Args>()
    var result: Result<Unit> = Result.success(Unit)

    override suspend fun invoke(
        target: ModelDeletionTarget,
        modelTypesNeedingReassignment: List<ModelType>,
        replacementLocalConfigId: LocalModelConfigurationId?,
        replacementApiConfigId: ApiModelConfigurationId?,
        replacementMediaProviderId: MediaProviderId?,
    ): Result<Unit> {
        calls += Args(target, modelTypesNeedingReassignment, replacementLocalConfigId, replacementApiConfigId, replacementMediaProviderId)
        return result
    }
}

/** Recording [DeleteModelPackUseCase] fake (ENC-28). */
class FakeDeleteModelPackUseCase :
    com.browntowndev.enclave.domain.usecase.modelconfig.DeleteModelPackUseCase(
        com.browntowndev.enclave.domain.usecase.modelconfig.DeleteLocalModelUseCase(
            FakeLocalModelRepository(),
            FakeDefaultModelRepository(),
            FakeModelFileScanner(),
            FakeApiModelRepository(),
            FakeLoggingPort(),
        ),
        FakeUtilityModelRepository(),
        FakeModelFileScanner(),
        com.browntowndev.enclave.domain.usecase.download.ResolveModelCatalogUseCase(FakeModelConfigFetcher()),
    ) {
    data class Args(
        val packId: String,
        val replacementLocalConfigId: LocalModelConfigurationId?,
        val replacementApiConfigId: ApiModelConfigurationId?,
        val replacementMediaProviderId: MediaProviderId?,
    )

    val calls = mutableListOf<Args>()
    var result: Result<Unit> = Result.success(Unit)

    override suspend fun invoke(
        packId: String,
        replacementLocalConfigId: LocalModelConfigurationId?,
        replacementApiConfigId: ApiModelConfigurationId?,
        replacementMediaProviderId: MediaProviderId?,
    ): Result<Unit> {
        calls += Args(packId, replacementLocalConfigId, replacementApiConfigId, replacementMediaProviderId)
        return result
    }
}

/** Recording [SaveTtsProviderUseCase] fake (ENC-28). */
class FakeSaveTtsProviderUseCase : SaveTtsProviderUseCase(FakeTtsProviderRepository()) {
    val calls = mutableListOf<TtsProviderDraft>()
    var result: Result<TtsProviderId> = Result.success(TtsProviderId("tts-1"))

    override suspend fun invoke(draft: TtsProviderDraft): Result<TtsProviderId> {
        calls += draft
        return result
    }
}

/** Recording [DeleteTtsProviderUseCase] fake (ENC-28). */
class FakeDeleteTtsProviderUseCase : DeleteTtsProviderUseCase(FakeTtsProviderRepository()) {
    val calls = mutableListOf<TtsProviderId>()

    override suspend fun invoke(id: TtsProviderId) {
        calls += id
    }
}

/** Recording [SaveLiveVoiceProviderUseCase] fake (ENC-28). */
class FakeSaveLiveVoiceProviderUseCase :
    SaveLiveVoiceProviderUseCase(com.browntowndev.enclave.core.testing.FakeLiveVoiceProviderRepository()) {
    val calls = mutableListOf<com.browntowndev.enclave.domain.usecase.settings.LiveVoiceProviderDraft>()
    var result: Result<LiveVoiceProviderId> = Result.success(LiveVoiceProviderId("lv-1"))

    override suspend fun invoke(
        draft: com.browntowndev.enclave.domain.usecase.settings.LiveVoiceProviderDraft,
    ): Result<LiveVoiceProviderId> {
        calls += draft
        return result
    }
}

/** Recording [DeleteLiveVoiceProviderUseCase] fake (ENC-28). */
class FakeDeleteLiveVoiceProviderUseCase :
    DeleteLiveVoiceProviderUseCase(com.browntowndev.enclave.core.testing.FakeLiveVoiceProviderRepository()) {
    val calls = mutableListOf<LiveVoiceProviderId>()

    /** Optional hook run inside invoke (cross-fake ordering assertions). */
    var onInvoke: (() -> Unit)? = null

    override suspend fun invoke(id: LiveVoiceProviderId) {
        calls += id
        onInvoke?.invoke()
    }
}

/** Recording [SaveMediaProviderUseCase] fake (ENC-28). */
class FakeSaveMediaProviderUseCase : SaveMediaProviderUseCase(FakeMediaProviderRepository()) {
    val calls = mutableListOf<Pair<MediaProviderAsset, String?>>()

    override suspend fun invoke(asset: MediaProviderAsset, apiKey: String?): MediaProviderId {
        calls += asset to apiKey
        return asset.id
    }
}

/** Recording [DeleteMediaProviderUseCase] fake (ENC-28). */
class FakeDeleteMediaProviderUseCase : DeleteMediaProviderUseCase(FakeMediaProviderRepository()) {
    val calls = mutableListOf<MediaProviderId>()

    override suspend fun invoke(id: MediaProviderId) {
        calls += id
    }
}

/** Recording [LoginWithGoogleUseCase] fake (ENC-28). */
class FakeLoginWithGoogleUseCase : LoginWithGoogleUseCase(FakeAuthManager(), FakeLoggingPort()) {
    var callCount = 0
    var result: Result<UserProfile> =
        Result.failure(IllegalStateException("FakeLoginWithGoogleUseCase.result not set"))

    override suspend fun invoke(): Result<UserProfile> {
        callCount++
        return result
    }
}

/** Recording [LogoutUseCase] fake (ENC-28). */
class FakeLogoutUseCase :
    LogoutUseCase(
        FakeAuthManager(),
        FakeCleanupRepository(),
        FakeCloudSyncPort(),
        com.browntowndev.enclave.domain.usecase.settings.AutoAssignLocalModelsUseCase(
            com.browntowndev.enclave.domain.usecase.modelconfig.GetLocalModelAssetsUseCaseImpl(FakeLocalModelRepository()),
            com.browntowndev.enclave.domain.usecase.byok.GetDefaultModelsUseCaseImpl(FakeDefaultModelRepository()),
            FakeSetDefaultModelUseCase(),
            com.browntowndev.enclave.domain.usecase.byok.GetApiModelAssetsUseCaseImpl(FakeApiModelRepository()),
            GetTtsProvidersUseCase(FakeTtsProviderRepository()),
            GetMediaProvidersUseCase(FakeMediaProviderRepository()),
            FakeLocalImageGenerationCapability(),
        ),
    ) {
    var callCount = 0
    var result: Result<Unit> = Result.success(Unit)

    override suspend fun invoke(): Result<Unit> {
        callCount++
        return result
    }
}

/** Recording [CreateVaultUseCase] fake (ENC-28), mirroring the app migration's. */
class FakeCreateVaultUseCase : CreateVaultUseCase(FakeCryptoManagerPort()) {
    val calls = mutableListOf<Pair<String, ByteArray>>()
    var result: Result<ByteArray> = Result.success(byteArrayOf(1, 2, 3))

    override suspend fun invoke(password: String, salt: ByteArray): Result<ByteArray> {
        calls += password to salt
        return result
    }
}

/** Recording [UnlockVaultFromStoredMetadataUseCase] fake (ENC-28). */
class FakeUnlockVaultFromStoredMetadataUseCase(
    private val events: MutableList<String>? = null,
) : UnlockVaultFromStoredMetadataUseCase(
        UnlockVaultUseCase(FakeCryptoManagerPort(), FakeApiModelRepository(), FakeApiKeyProvider()),
        FakeCryptoManagerPort(),
        FakeVaultMetadataPort(),
        FakeCloudSyncPort(),
    ) {
    val calls = mutableListOf<String>()
    var result: Result<Unit> = Result.success(Unit)

    override suspend fun invoke(password: String): Result<Unit> {
        calls += password
        events?.add("unlock")
        return result
    }
}

/** Recording [DetectVaultReconciliationStateUseCase] fake (ENC-28), mirroring the app migration's. */
class FakeDetectVaultReconciliationStateUseCase(
    private val events: MutableList<String>? = null,
) : DetectVaultReconciliationStateUseCase(FakeCryptoManagerPort(), FakeVaultMetadataPort(), FakeCloudSyncPort()) {
    var result: Result<com.browntowndev.enclave.domain.usecase.security.VaultReconciliationOutcome> =
        Result.success(com.browntowndev.enclave.domain.usecase.security.VaultReconciliationOutcome.NoCloudVault)
    val calls = mutableListOf<String>()

    override suspend fun invoke(
        password: String,
    ): Result<com.browntowndev.enclave.domain.usecase.security.VaultReconciliationOutcome> {
        calls += password
        events?.add("detect")
        return result
    }
}

/** Recording [AdoptCloudVaultUseCase] fake (ENC-28), mirroring the app migration's. */
class FakeAdoptCloudVaultUseCase :
    AdoptCloudVaultUseCase(
        FakeCryptoManagerPort(),
        FakeVaultMetadataPort(),
        FakeCleanupRepository(),
        com.browntowndev.enclave.core.testing.FakeBiometricPort(),
    ) {
    val calls =
        mutableListOf<com.browntowndev.enclave.domain.usecase.security.VaultReconciliationOutcome.MismatchRequiresClobber>()
    var result: Result<Unit> = Result.success(Unit)

    override suspend fun invoke(
        payload: com.browntowndev.enclave.domain.usecase.security.VaultReconciliationOutcome.MismatchRequiresClobber,
    ): Result<Unit> {
        calls += payload
        return result
    }
}

/** Recording [BootstrapCloudVaultFromLocalUseCase] fake (ENC-28), mirroring the app migration's. */
class FakeBootstrapCloudVaultFromLocalUseCase :
    BootstrapCloudVaultFromLocalUseCase(FakeVaultMetadataPort(), FakeCloudSyncPort()) {
    var callCount = 0
    var result: Result<Unit> = Result.success(Unit)

    override suspend fun invoke(): Result<Unit> {
        callCount++
        return result
    }
}

/** Recording [StartCloudSyncListeningUseCase] fake (ENC-28), mirroring the app migration's. */
class FakeStartCloudSyncListeningUseCase(
    private val events: MutableList<String>? = null,
) : StartCloudSyncListeningUseCase(
        FakeCloudSyncPort(),
        com.browntowndev.enclave.core.testing.FakeEntitlementPort(),
    ) {
    val calls = mutableListOf<Boolean>()
    var result: StartCloudSyncResult = StartCloudSyncResult.Started

    override suspend fun invoke(
        uploadLocalFirst: Boolean,
        onProgress: (suspend (com.browntowndev.enclave.domain.model.sync.CloudSyncReplayProgress) -> Unit)?,
    ): StartCloudSyncResult {
        calls += uploadLocalFirst
        events?.add("startSync")
        return result
    }
}

/** Recording [DeleteAllChatsUseCase] fake (ENC-28). */
class FakeDeleteAllChatsUseCase : DeleteAllChatsUseCase(FakeChatRepository()) {
    var callCount = 0

    /** When set, invoke throws this after recording the call. */
    var failure: Throwable? = null

    override suspend fun invoke() {
        callCount++
        failure?.let { throw it }
    }
}

/** Recording [DeleteAllMediaUseCase] fake (ENC-28). */
class FakeDeleteAllMediaUseCase : DeleteAllMediaUseCase(FakeStudioRepository()) {
    var callCount = 0

    override suspend fun invoke() {
        callCount++
    }
}

/** Recording [DeleteAllMemoriesUseCase] fake (ENC-28). */
class FakeDeleteAllMemoriesUseCase : DeleteAllMemoriesUseCase(FakeMemoriesRepository()) {
    var callCount = 0

    override suspend fun invoke() {
        callCount++
    }
}

/** Recording [DeleteAllArtifactsUseCase] fake (ENC-28). */
class FakeDeleteAllArtifactsUseCase : DeleteAllArtifactsUseCase(FakeCleanupRepository()) {
    var callCount = 0

    override suspend fun invoke() {
        callCount++
    }
}

/** Recording [DeleteAccountUseCase] fake (ENC-28). */
class FakeDeleteAccountUseCase : DeleteAccountUseCase(FakeCloudSyncPort(), FakeAuthManager()) {
    var callCount = 0
    var result: Result<Unit> = Result.success(Unit)

    /** When set, invoke throws this after recording the call. */
    var failure: Throwable? = null

    override suspend fun invoke(): Result<Unit> {
        callCount++
        failure?.let { throw it }
        return result
    }
}

/** Fake [com.browntowndev.enclave.domain.usecase.settings.SettingsPreferencesUseCases] bundle (ENC-28). */
class FakeSettingsPreferencesUseCases(
    override val getSettings: FakeGetSettingsUseCase = FakeGetSettingsUseCase(),
    override val updateTheme: FakeUpdateThemeUseCase = FakeUpdateThemeUseCase(),
    override val updateHapticResponse: FakeUpdateHapticResponseUseCase = FakeUpdateHapticResponseUseCase(),
    override val updateCustomizationEnabled: FakeUpdateCustomizationEnabledUseCase = FakeUpdateCustomizationEnabledUseCase(),
    override val updateSelectedPromptOption: FakeUpdateSelectedPromptOptionUseCase = FakeUpdateSelectedPromptOptionUseCase(),
    override val updateCustomPromptText: FakeUpdateCustomPromptTextUseCase = FakeUpdateCustomPromptTextUseCase(),
    override val updateAllowMemories: FakeUpdateAllowMemoriesUseCase = FakeUpdateAllowMemoriesUseCase(),
    override val updateSearchEnabled: FakeUpdateSearchEnabledUseCase = FakeUpdateSearchEnabledUseCase(),
    override val updateSearchProvider: FakeUpdateSearchProviderUseCase = FakeUpdateSearchProviderUseCase(),
    override val saveTavilyApiKey: FakeSaveTavilyApiKeyUseCase = FakeSaveTavilyApiKeyUseCase(),
    override val clearTavilyApiKey: FakeClearTavilyApiKeyUseCase = FakeClearTavilyApiKeyUseCase(),
    override val saveExaApiKey: FakeSaveExaApiKeyUseCase = FakeSaveExaApiKeyUseCase(),
    override val clearExaApiKey: FakeClearExaApiKeyUseCase = FakeClearExaApiKeyUseCase(),
    override val updateXSearchEnabled: FakeUpdateXSearchEnabledUseCase = FakeUpdateXSearchEnabledUseCase(),
    override val saveXApiKey: FakeSaveXApiKeyUseCase = FakeSaveXApiKeyUseCase(),
    override val clearXApiKey: FakeClearXApiKeyUseCase = FakeClearXApiKeyUseCase(),
    override val updateShowSearchProviderSwitchAlert: FakeUpdateShowSearchProviderSwitchAlertUseCase = FakeUpdateShowSearchProviderSwitchAlertUseCase(),
    override val updateBackgroundInferenceEnabled: FakeUpdateBackgroundInferenceEnabledUseCase = FakeUpdateBackgroundInferenceEnabledUseCase(),
    override val updateAutomaticErrorTrackingEnabled: FakeUpdateAutomaticErrorTrackingEnabledUseCase = FakeUpdateAutomaticErrorTrackingEnabledUseCase(),
) : com.browntowndev.enclave.domain.usecase.settings.SettingsPreferencesUseCases

/** Fake [com.browntowndev.enclave.domain.usecase.settings.SettingsLocalModelUseCases] bundle (ENC-28). */
class FakeSettingsLocalModelUseCases(
    override val getLocalModelAssets: FakeGetLocalModelAssetsUseCase = FakeGetLocalModelAssetsUseCase(),
    override val getRestorableLocalModels: FakeGetRestorableLocalModelsUseCase = FakeGetRestorableLocalModelsUseCase(),
    override val saveLocalModelPreset: FakeSaveLocalModelPresetUseCase = FakeSaveLocalModelPresetUseCase(),
    override val reDownloadModel: FakeReDownloadModelUseCase = FakeReDownloadModelUseCase(),
    override val getModelPacksForSettings: FakeGetModelPacksForSettingsUseCase = FakeGetModelPacksForSettingsUseCase(),
    override val observeActiveUtilityModels: FakeObserveActiveUtilityModelsUseCase = FakeObserveActiveUtilityModelsUseCase(),
    override val reDownloadModelPack: FakeReDownloadModelPackUseCase = FakeReDownloadModelPackUseCase(),
    override val deleteModelPack: FakeDeleteModelPackUseCase = FakeDeleteModelPackUseCase(),
) : com.browntowndev.enclave.domain.usecase.settings.SettingsLocalModelUseCases

/** Fake [com.browntowndev.enclave.domain.usecase.settings.SettingsApiProviderUseCases] bundle (ENC-28). */
class FakeSettingsApiProviderUseCases(
    override val getApiModelAssets: FakeGetApiModelAssetsUseCase = FakeGetApiModelAssetsUseCase(),
    override val saveApiProviderDraft: FakeSaveApiProviderDraftUseCase = FakeSaveApiProviderDraftUseCase(),
    override val saveApiPreset: FakeSaveApiPresetUseCase = FakeSaveApiPresetUseCase(),
    override val discoverApiModels: FakeDiscoverApiModelsUseCase = FakeDiscoverApiModelsUseCase(),
    override val applyApiModelMetadataDefaults: ApplyApiModelMetadataDefaultsUseCase = ApplyApiModelMetadataDefaultsUseCase(),
    override val testApiProviderConnection: FakeTestApiProviderConnectionUseCase = FakeTestApiProviderConnectionUseCase(),
    override val fetchOpenRouterModelEndpoints: FakeFetchOpenRouterModelEndpointsUseCase = FakeFetchOpenRouterModelEndpointsUseCase(),
) : com.browntowndev.enclave.domain.usecase.settings.SettingsApiProviderUseCases

/** Fake [com.browntowndev.enclave.domain.usecase.settings.SettingsAssignmentUseCases] bundle (ENC-28). */
class FakeSettingsAssignmentUseCases(
    override val getDefaultModels: FakeGetDefaultModelsUseCase = FakeGetDefaultModelsUseCase(),
    override val setDefaultModel: FakeSetDefaultModelUseCase = FakeSetDefaultModelUseCase(),
    override val clearDefaultModel: FakeClearDefaultModelUseCase = FakeClearDefaultModelUseCase(),
    override val resolveAssignedModelSelection: FakeResolveAssignedModelSelectionUseCase = FakeResolveAssignedModelSelectionUseCase(),
) : com.browntowndev.enclave.domain.usecase.settings.SettingsAssignmentUseCases

/** Fake [com.browntowndev.enclave.domain.usecase.settings.SettingsDeletionUseCases] bundle (ENC-28). */
class FakeSettingsDeletionUseCases(
    override val prepareModelDeletion: FakePrepareModelDeletionUseCase = FakePrepareModelDeletionUseCase(),
    override val executeModelDeletionWithReassignment: FakeExecuteModelDeletionWithReassignmentUseCase = FakeExecuteModelDeletionWithReassignmentUseCase(),
) : com.browntowndev.enclave.domain.usecase.settings.SettingsDeletionUseCases

/** Fake [com.browntowndev.enclave.domain.usecase.settings.SettingsTtsUseCases] bundle (ENC-28). */
class FakeSettingsTtsUseCases(
    override val getTtsProviders: FakeGetTtsProvidersUseCase = FakeGetTtsProvidersUseCase(),
    override val saveTtsProvider: FakeSaveTtsProviderUseCase = FakeSaveTtsProviderUseCase(),
    override val deleteTtsProvider: FakeDeleteTtsProviderUseCase = FakeDeleteTtsProviderUseCase(),
) : com.browntowndev.enclave.domain.usecase.settings.SettingsTtsUseCases

/** Fake [com.browntowndev.enclave.domain.usecase.settings.SettingsLiveVoiceUseCases] bundle (ENC-28). */
class FakeSettingsLiveVoiceUseCases(
    override val getLiveVoiceProviders: FakeGetLiveVoiceProvidersUseCase = FakeGetLiveVoiceProvidersUseCase(),
    override val saveLiveVoiceProvider: FakeSaveLiveVoiceProviderUseCase = FakeSaveLiveVoiceProviderUseCase(),
    override val deleteLiveVoiceProvider: FakeDeleteLiveVoiceProviderUseCase = FakeDeleteLiveVoiceProviderUseCase(),
) : com.browntowndev.enclave.domain.usecase.settings.SettingsLiveVoiceUseCases

/** Fake [com.browntowndev.enclave.domain.usecase.settings.SettingsMediaUseCases] bundle (ENC-28). */
class FakeSettingsMediaUseCases(
    override val getMediaProviders: FakeGetMediaProvidersUseCase = FakeGetMediaProvidersUseCase(),
    override val saveMediaProvider: FakeSaveMediaProviderUseCase = FakeSaveMediaProviderUseCase(),
    override val deleteMediaProvider: FakeDeleteMediaProviderUseCase = FakeDeleteMediaProviderUseCase(),
) : com.browntowndev.enclave.domain.usecase.settings.SettingsMediaUseCases

/** Fake [com.browntowndev.enclave.domain.usecase.settings.SettingsAuthUseCases] bundle (ENC-28). */
class FakeSettingsAuthUseCases(
    override val loginWithGoogle: FakeLoginWithGoogleUseCase = FakeLoginWithGoogleUseCase(),
    override val logout: FakeLogoutUseCase = FakeLogoutUseCase(),
    override val getAuthState: FakeGetAuthStateUseCase = FakeGetAuthStateUseCase(),
) : com.browntowndev.enclave.domain.usecase.settings.SettingsAuthUseCases

/** Fake [com.browntowndev.enclave.domain.usecase.settings.SettingsMemoriesUseCases] over [FakeMemoriesRepository] (ENC-28). */
class FakeSettingsMemoriesUseCases(
    val repository: FakeMemoriesRepository = FakeMemoriesRepository(),
) : com.browntowndev.enclave.domain.usecase.settings.SettingsMemoriesUseCases {
    override fun getAllMemoriesFlow(): Flow<List<com.browntowndev.enclave.domain.model.memory.Memory>> =
        repository.getAllMemoriesFlow()

    override suspend fun insertMemory(
        category: com.browntowndev.enclave.domain.model.memory.MemoryCategory,
        content: String,
    ): com.browntowndev.enclave.domain.model.memory.Memory = repository.insertMemory(category, content)

    override suspend fun updateMemory(
        id: String,
        content: String,
        category: com.browntowndev.enclave.domain.model.memory.MemoryCategory?,
    ) = repository.updateMemory(id, content, category)

    override suspend fun deleteMemory(id: String) = repository.deleteMemory(id)
}

/** Fake [com.browntowndev.enclave.domain.usecase.settings.SettingsDataControlsUseCases] bundle (ENC-28). */
class FakeSettingsDataControlsUseCases(
    override val deleteAllChats: FakeDeleteAllChatsUseCase = FakeDeleteAllChatsUseCase(),
    override val deleteAllMedia: FakeDeleteAllMediaUseCase = FakeDeleteAllMediaUseCase(),
    override val deleteAllMemories: FakeDeleteAllMemoriesUseCase = FakeDeleteAllMemoriesUseCase(),
    override val deleteAllArtifacts: FakeDeleteAllArtifactsUseCase = FakeDeleteAllArtifactsUseCase(),
    override val deleteAccount: FakeDeleteAccountUseCase = FakeDeleteAccountUseCase(),
) : com.browntowndev.enclave.domain.usecase.settings.SettingsDataControlsUseCases

/** Fake umbrella [com.browntowndev.enclave.domain.usecase.settings.SettingsUseCases] (ENC-28). */
class FakeSettingsUseCases(
    override val preferences: FakeSettingsPreferencesUseCases = FakeSettingsPreferencesUseCases(),
    override val localModels: FakeSettingsLocalModelUseCases = FakeSettingsLocalModelUseCases(),
    override val apiProviders: FakeSettingsApiProviderUseCases = FakeSettingsApiProviderUseCases(),
    override val assignments: FakeSettingsAssignmentUseCases = FakeSettingsAssignmentUseCases(),
    override val deletion: FakeSettingsDeletionUseCases = FakeSettingsDeletionUseCases(),
    override val tts: FakeSettingsTtsUseCases = FakeSettingsTtsUseCases(),
    override val liveVoice: FakeSettingsLiveVoiceUseCases = FakeSettingsLiveVoiceUseCases(),
    override val media: FakeSettingsMediaUseCases = FakeSettingsMediaUseCases(),
    override val auth: FakeSettingsAuthUseCases = FakeSettingsAuthUseCases(),
    override val memories: FakeSettingsMemoriesUseCases = FakeSettingsMemoriesUseCases(),
    override val dataControls: FakeSettingsDataControlsUseCases = FakeSettingsDataControlsUseCases(),
) : com.browntowndev.enclave.domain.usecase.settings.SettingsUseCases
