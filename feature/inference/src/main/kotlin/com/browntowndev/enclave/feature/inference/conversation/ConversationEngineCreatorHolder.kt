package com.browntowndev.enclave.feature.inference.conversation

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine

internal class ConversationEngineCreatorHolder {
    var override: LiteRtEngineCreator? = null

    fun create(
        modelPath: String,
        backend: Backend,
        visionBackend: Backend?,
        contextWindow: Int,
        cacheDir: String?,
        defaultCreator: LiteRtEngineCreator,
    ): Engine =
        override?.invoke(modelPath, backend, visionBackend, contextWindow, cacheDir)
            ?: defaultCreator(modelPath, backend, visionBackend, contextWindow, cacheDir)
}
