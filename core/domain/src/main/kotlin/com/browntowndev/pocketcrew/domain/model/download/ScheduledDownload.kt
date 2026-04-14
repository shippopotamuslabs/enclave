package com.browntowndev.pocketcrew.domain.model.download

import com.browntowndev.pocketcrew.domain.model.config.LocalModelId

/**
 * Return model for callers that need the scheduled session id.
 * Useful for UI progress observation after scheduling a request.
 */
data class ScheduledDownload(
    val sessionId: String,
    val requestKind: DownloadRequestKind,
    val targetModelId: LocalModelId? = null,
)