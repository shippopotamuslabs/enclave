package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.domain.port.inference.ActiveChatTurnKey
import com.browntowndev.enclave.domain.port.inference.ActiveChatTurnSnapshotPort
import javax.inject.Inject

class ObserveActiveChatTurnSnapshotUseCase @Inject constructor(
    private val port: ActiveChatTurnSnapshotPort,
) {
    operator fun invoke(key: ActiveChatTurnKey) = port.observe(key)
}

class ClearActiveChatTurnSnapshotUseCase @Inject constructor(
    private val port: ActiveChatTurnSnapshotPort,
) {
    suspend operator fun invoke(key: ActiveChatTurnKey) = port.clear(key)
}

class MarkWebSearchSourcesExtractedUseCase @Inject constructor(
    private val port: ActiveChatTurnSnapshotPort,
) {
    suspend operator fun invoke(key: ActiveChatTurnKey, urls: List<String>) =
        port.markWebSearchSourcesExtracted(key, urls)
}
