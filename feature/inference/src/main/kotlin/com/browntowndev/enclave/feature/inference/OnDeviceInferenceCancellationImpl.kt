package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.port.inference.OnDeviceInferenceCancellationPort
import com.browntowndev.enclave.domain.qualifier.ApplicationScope
import com.browntowndev.enclave.feature.inference.llama.LlamaChatSessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class OnDeviceInferenceCancellationImpl
    @Inject
    constructor(
        @param:ApplicationScope private val applicationScope: CoroutineScope,
        private val conversationManager: ConversationManager,
        private val llamaChatSessionManagerProvider: Provider<LlamaChatSessionManager>,
    ) : OnDeviceInferenceCancellationPort {
        override fun cancelActiveGeneration() {
            conversationManager.cancelProcess()
            conversationManager.cancelCurrentGeneration()
            applicationScope.launch(Dispatchers.Default) {
                try {
                    llamaChatSessionManagerProvider.get().stopCurrentGeneration()
                } catch (_: Exception) {
                    // Best-effort stop; engine may already be idle or shut down.
                }
            }
        }
    }
