package com.browntowndev.enclave.feature.settings

import com.browntowndev.enclave.core.ui.error.ViewModelErrorHandler
import com.browntowndev.enclave.domain.usecase.settings.SettingsApiProviderUseCases
import com.browntowndev.enclave.domain.usecase.settings.SettingsMediaUseCases
import com.browntowndev.enclave.domain.usecase.settings.SettingsLiveVoiceUseCases
import com.browntowndev.enclave.domain.usecase.settings.SettingsTtsUseCases
import com.browntowndev.enclave.core.testing.FakeFetchApiProviderModelDetailUseCase
import com.browntowndev.enclave.core.testing.FakeLoggingPort

internal fun createSettingsApiProvidersDelegate(
    apiProviderUseCases: SettingsApiProviderUseCases,
    ttsUseCases: SettingsTtsUseCases,
    liveVoiceUseCases: SettingsLiveVoiceUseCases,
    mediaUseCases: SettingsMediaUseCases,
    deletionHelper: SettingsDeletionHelper,
    apiModelAssetUiMapper: ApiModelAssetUiMapper = ApiModelAssetUiMapper(),
    errorHandler: ViewModelErrorHandler,
): SettingsApiProvidersDelegate =
    SettingsApiProvidersDelegate(
        byokCoordinator =
            ByokProvidersCoordinator(
                apiProviderUseCases = apiProviderUseCases,
                deletionHelper = deletionHelper,
                apiModelAssetUiMapper = apiModelAssetUiMapper,
                errorHandler = errorHandler,
                loggingPort = FakeLoggingPort(),
            ),
        ttsCoordinator =
            TtsProvidersCoordinator(
                apiProviderUseCases = apiProviderUseCases,
                ttsUseCases = ttsUseCases,
                errorHandler = errorHandler,
            ),
        liveVoiceCoordinator =
            LiveVoiceProvidersCoordinator(
                apiProviderUseCases = apiProviderUseCases,
                liveVoiceUseCases = liveVoiceUseCases,
                errorHandler = errorHandler,
            ),
        mediaCoordinator =
            MediaProvidersCoordinator(
                apiProviderUseCases = apiProviderUseCases,
                mediaUseCases = mediaUseCases,
                fetchApiProviderModelDetailUseCase = FakeFetchApiProviderModelDetailUseCase(),
                errorHandler = errorHandler,
            ),
    )
