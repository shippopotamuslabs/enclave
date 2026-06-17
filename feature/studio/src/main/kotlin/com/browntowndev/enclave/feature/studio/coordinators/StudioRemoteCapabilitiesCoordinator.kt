package com.browntowndev.enclave.feature.studio.coordinators

import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.config.MediaProviderAsset
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.media.ProviderCapabilities
import com.browntowndev.enclave.domain.usecase.media.ResolveMediaProviderCapabilitiesUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
class StudioRemoteCapabilitiesCoordinator
    @Inject
    constructor(
        private val resolveMediaProviderCapabilitiesUseCase: ResolveMediaProviderCapabilitiesUseCase,
    ) {
        fun attach(
            scope: CoroutineScope,
            activeProviderFlow: Flow<MediaProviderAsset?>,
            mediaType: Flow<MediaCapability>,
            capabilities: MutableStateFlow<ProviderCapabilities?>,
            isCapabilitiesLoading: MutableStateFlow<Boolean>,
        ) {
            combine(activeProviderFlow, mediaType) { provider, mediaCapability ->
                provider to mediaCapability
            }.distinctUntilChanged { previous, next ->
                previous.second == next.second &&
                    remoteCapabilitiesRequestKey(previous.first) ==
                    remoteCapabilitiesRequestKey(next.first)
            }.flatMapLatest { (provider, mediaCapability) ->
                if (provider == null) {
                    // Local image-generation assignments expose capabilities via MultimodalViewModel's
                    // localImageCapabilitiesFlow; do not overwrite that state from the remote path.
                    kotlinx.coroutines.flow.emptyFlow()
                } else if (requiresRemoteCapabilities(provider)) {
                    flow {
                        emit(
                            CapabilitiesLoadSnapshot(
                                isLoading = true,
                                capabilities = null,
                            ),
                        )
                        val resolved =
                            resolveMediaProviderCapabilitiesUseCase(
                                mediaProvider = provider,
                                mediaCapability = mediaCapability,
                            )
                        emit(
                            CapabilitiesLoadSnapshot(
                                isLoading = false,
                                capabilities = resolved,
                            ),
                        )
                    }
                } else {
                    flow {
                        val resolved =
                            resolveMediaProviderCapabilitiesUseCase(
                                mediaProvider = provider,
                                mediaCapability = mediaCapability,
                            )
                        emit(
                            CapabilitiesLoadSnapshot(
                                isLoading = false,
                                capabilities = resolved,
                            ),
                        )
                    }
                }
            }.onEach { snapshot ->
                isCapabilitiesLoading.value = snapshot.isLoading
                capabilities.value = snapshot.capabilities
            }.launchIn(scope)
        }

        fun requiresRemoteCapabilitiesLoading(provider: MediaProviderAsset?): Boolean = requiresRemoteCapabilities(provider)

        private data class CapabilitiesLoadSnapshot(
            val isLoading: Boolean,
            val capabilities: ProviderCapabilities?,
        )

        private fun requiresRemoteCapabilities(provider: MediaProviderAsset?): Boolean {
            if (provider == null || provider.provider != ApiProvider.FAL) return false
            return !provider.modelName.isNullOrBlank()
        }

        private fun remoteCapabilitiesRequestKey(provider: MediaProviderAsset?): String =
            buildString {
                append(provider?.id?.value.orEmpty())
                append('|')
                append(provider?.provider?.name.orEmpty())
                append('|')
                append(provider?.modelName.orEmpty())
            }
    }
