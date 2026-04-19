package com.browntowndev.pocketcrew.domain.model.download

import com.browntowndev.pocketcrew.domain.model.config.LocalModelId

/**
 * Scheduler request model containing all information needed to execute a download.
 *
 * Rules:
 * - [targetModelId] must be null for [DownloadRequestKind.INITIALIZE_MODELS]
 * - [targetModelId] must be non-null for [DownloadRequestKind.RESTORE_SOFT_DELETED_MODEL]
 */
data class DownloadWorkRequest(
    val files: List<DownloadFileSpec>,
    val sessionId: String,
    val requestKind: DownloadRequestKind,
    val targetModelId: LocalModelId? = null,
    val wifiOnly: Boolean = true,
)