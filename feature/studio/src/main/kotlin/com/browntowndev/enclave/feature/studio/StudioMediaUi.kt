package com.browntowndev.enclave.feature.studio

import androidx.compose.runtime.Immutable
import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.port.repository.StudioMediaAsset

@Immutable
data class StudioMediaUi(
    val id: String,
    val localUri: String,
    val remoteUrl: String? = null,
    val prompt: String,
    val mediaType: MediaCapability,
    val createdAt: Long,
    val albumId: String? = null,
    val aspectRatio: Float? = null,
)

fun StudioMediaAsset.toUi(): StudioMediaUi =
    StudioMediaUi(
        id = id,
        localUri = localUri,
        remoteUrl = remoteUrl,
        prompt = prompt,
        mediaType =
            when (mediaType) {
                MediaCapability.VIDEO.name -> MediaCapability.VIDEO
                MediaCapability.MUSIC.name -> MediaCapability.MUSIC
                else -> MediaCapability.IMAGE
            },
        createdAt = createdAt,
        albumId = albumId,
    )

val StudioMediaUi.bestUri: Any? get() {
    val path = localUri.replace("file://", "")
    return if (java.io.File(path).exists()) localUri else remoteUrl
}

/**
 * True while the bytes for a cloud-synced item still have to be downloaded + decrypted locally
 * (the row arrived as metadata pointing at the remote URL, no local file yet). Drives the gallery
 * download spinner. Flips to false automatically once [MediaCloudDownloadWorker] materializes the
 * file and the row re-emits with a `file://` [localUri].
 */
val StudioMediaUi.isAwaitingDownload: Boolean get() {
    if (remoteUrl.isNullOrBlank()) return false
    if (localUri.startsWith("content://")) return false
    val path = localUri.replace("file://", "")
    if (path.isBlank() || path.startsWith("http")) return true
    return !java.io.File(path).exists()
}

internal fun StudioMediaUi.effectiveLocalUri(): String {
    val best = bestUri
    return if (best is String) best else localUri
}
