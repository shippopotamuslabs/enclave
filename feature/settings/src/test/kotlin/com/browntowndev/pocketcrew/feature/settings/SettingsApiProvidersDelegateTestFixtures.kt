package com.browntowndev.pocketcrew.feature.settings

import com.browntowndev.pocketcrew.core.ui.error.ViewModelErrorHandler
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsApiProviderUseCases
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsMediaUseCases
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsTtsUseCases

internal fun createSettingsApiProvidersDelegate(
    apiProviderUseCases: SettingsApiProviderUseCases,
    ttsUseCases: SettingsTtsUseCases,
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
            ),
        ttsCoordinator =
            TtsProvidersCoordinator(
                apiProviderUseCases = apiProviderUseCases,
                ttsUseCases = ttsUseCases,
                errorHandler = errorHandler,
            ),
        mediaCoordinator =
            MediaProvidersCoordinator(
                apiProviderUseCases = apiProviderUseCases,
                mediaUseCases = mediaUseCases,
                errorHandler = errorHandler,
            ),
    )
