package com.browntowndev.pocketcrew.app.tts

import com.browntowndev.pocketcrew.domain.port.media.TtsPlaybackRegistryPort
import com.browntowndev.pocketcrew.domain.port.media.TtsPlaybackRequest
import com.browntowndev.pocketcrew.domain.port.media.TtsPlaybackStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thread-safe in-memory registry that maps TTS request IDs to their playback metadata.
 *
 * This bridges the gap between the ViewModel (which creates requests) and the
 * Media3 DataSource (which resolves `pocketcrew-tts://play/{requestId}` URIs
 * to audio streams). No text is stored in URI query parameters; the DataSource
 * looks up the request by ID.
 */
@Singleton
class InMemoryTtsPlaybackRegistry
    @Inject
    constructor() : TtsPlaybackRegistryPort {
        private val requests = ConcurrentHashMap<String, RegisteredTtsPlayback>()

        override fun register(request: TtsPlaybackRequest): String {
            requests[request.requestId] = RegisteredTtsPlayback(request)
            return request.requestId
        }

        override fun resolve(requestId: String): TtsPlaybackRequest? = requests[requestId]?.request

        override fun observeStatus(requestId: String): Flow<TtsPlaybackStatus>? = requests[requestId]?.status?.asStateFlow()

        override fun publishStatus(
            requestId: String,
            status: TtsPlaybackStatus,
        ): Boolean {
            val playback = requests[requestId] ?: return false
            playback.status.value = status
            return true
        }

        override fun remove(requestId: String): Boolean = requests.remove(requestId) != null

        override fun clear() {
            requests.clear()
        }

        private data class RegisteredTtsPlayback(
            val request: TtsPlaybackRequest,
            val status: MutableStateFlow<TtsPlaybackStatus> = MutableStateFlow(TtsPlaybackStatus.Initializing),
        )
    }
