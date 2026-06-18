package com.browntowndev.enclave.feature.settings

import com.browntowndev.enclave.core.ui.error.ViewModelErrorHandler
import com.browntowndev.enclave.domain.usecase.byok.GetApiModelAssetsUseCase
import com.browntowndev.enclave.domain.usecase.settings.ApplyApiModelMetadataDefaultsUseCase
import com.browntowndev.enclave.domain.usecase.settings.DiscoverApiModelsUseCase
import com.browntowndev.enclave.domain.usecase.settings.EmptySettingsLiveVoiceUseCases
import com.browntowndev.enclave.domain.usecase.settings.SaveApiPresetUseCase
import com.browntowndev.enclave.domain.usecase.settings.SaveApiProviderDraftUseCase
import com.browntowndev.enclave.domain.usecase.settings.SettingsApiProviderUseCases
import com.browntowndev.enclave.domain.usecase.settings.SettingsLiveVoiceUseCases
import com.browntowndev.enclave.domain.usecase.settings.TestApiProviderConnectionUseCase
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

internal object UnwiredSettingsApiProviderUseCases : SettingsApiProviderUseCases {
    private fun <T> unwired(name: String): T = error("SettingsApiProviderUseCases.$name is not wired")

    override val getApiModelAssets: GetApiModelAssetsUseCase
        get() = unwired("getApiModelAssets")
    override val saveApiProviderDraft: SaveApiProviderDraftUseCase
        get() = unwired("saveApiProviderDraft")
    override val saveApiPreset: SaveApiPresetUseCase
        get() = unwired("saveApiPreset")
    override val discoverApiModels: DiscoverApiModelsUseCase
        get() = unwired("discoverApiModels")
    override val applyApiModelMetadataDefaults: ApplyApiModelMetadataDefaultsUseCase
        get() = unwired("applyApiModelMetadataDefaults")
    override val testApiProviderConnection: TestApiProviderConnectionUseCase
        get() = unwired("testApiProviderConnection")
    override val fetchOpenRouterModelEndpoints: com.browntowndev.enclave.domain.usecase.byok.FetchOpenRouterModelEndpointsUseCase
        get() = unwired("fetchOpenRouterModelEndpoints")
}

internal object NoOpViewModelErrorHandler : ViewModelErrorHandler {
    private val events = MutableSharedFlow<String>(extraBufferCapacity = 1)

    override val errorEvents: SharedFlow<String> = events.asSharedFlow()

    override fun handleError(
        tag: String,
        message: String,
        throwable: Throwable,
        userMessage: String,
    ) = Unit

    override fun coroutineExceptionHandler(
        tag: String,
        message: String,
        userMessage: String,
    ): CoroutineExceptionHandler = CoroutineExceptionHandler { _, _ -> }
}

internal fun defaultLiveVoiceProvidersCoordinator(): LiveVoiceProvidersCoordinator =
    LiveVoiceProvidersCoordinator(
        apiProviderUseCases = UnwiredSettingsApiProviderUseCases,
        liveVoiceUseCases = EmptySettingsLiveVoiceUseCases,
        errorHandler = NoOpViewModelErrorHandler,
    )
