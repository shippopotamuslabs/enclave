package com.browntowndev.pocketcrew.domain.port.media

import kotlinx.coroutines.flow.Flow

/**
 * Port for an in-memory registry that maps TTS request IDs to their playback metadata.
 *
 * The registry bridges the gap between the ViewModel (which creates requests) and
 * the Media3 DataSource (which resolves URIs to audio streams). Because Media3
 * accesses audio via URI, and we don't put text in query parameters, the DataSource
 * looks up the request metadata by ID.
 *
 * Implementations must be thread-safe.
 */
interface TtsPlaybackRegistryPort {
    /**
     * Registers a playback request and returns its unique request ID.
     * The request ID is also the path component in the URI: `pocketcrew-tts://play/{requestId}`.
     *
     * @param request The playback request to register.
     * @return The unique request ID (same as [TtsPlaybackRequest.requestId]).
     */
    fun register(request: TtsPlaybackRequest): String

    /**
     * Resolves a request ID to its playback request metadata.
     *
     * @param requestId The request ID from the URI path.
     * @return The [TtsPlaybackRequest] if found and not expired, null otherwise.
     */
    fun resolve(requestId: String): TtsPlaybackRequest?

    /**
     * Observes playback status for a registered request.
     *
     * @param requestId The request ID to observe.
     * @return A flow of playback status updates, or null if the request is not registered.
     */
    fun observeStatus(requestId: String): Flow<TtsPlaybackStatus>?

    /**
     * Publishes the latest playback status for a registered request.
     *
     * @param requestId The request ID whose status changed.
     * @param status The latest playback status.
     * @return True if the request was found and updated, false otherwise.
     */
    fun publishStatus(requestId: String, status: TtsPlaybackStatus): Boolean

    /**
     * Removes a request from the registry. Called after playback completes,
     * errors out, or is explicitly stopped.
     *
     * @param requestId The request ID to remove.
     * @return True if the request was found and removed, false otherwise.
     */
    fun remove(requestId: String): Boolean

    /**
     * Removes all requests from the registry. Useful for cleanup on service teardown.
     */
    fun clear()
}
