package com.browntowndev.pocketcrew.domain.model.download

import kotlinx.serialization.Serializable

/**
 * Pure transfer descriptor used by ModelDownloadWorker.
 *
 * Describes only what the transfer worker needs:
 * - file names and paths
 * - SHA256 for deduplication and integrity
 * - size for progress estimation
 * - source/origin metadata
 *
 * Must NOT include:
 * - modelType (not a transfer concern)
 * - prompt/system preset fields
 * - temperature/top-p/top-k/etc.
 * - any repository-only state
 */
@Serializable
data class DownloadFileSpec(
    val remoteFileName: String,
    val localFileName: String,
    val sha256: String,
    val sizeInBytes: Long,
    val huggingFaceModelName: String = "",
    val huggingFacePath: String? = null,
    val source: String = "HUGGING_FACE",
    val modelFileFormat: String = "LITERTLM",
    val utilityType: String? = null,
    val mmprojRemoteFileName: String? = null,
    val mmprojLocalFileName: String? = null,
    val mmprojSha256: String? = null,
    val mmprojSizeInBytes: Long? = null,
)

/**
 * A single file artifact to download, derived from a DownloadFileSpec.
 * For models with mmproj, a single spec expands to two artifacts.
 */
data class DownloadArtifact(
    val remoteFileName: String,
    val localFileName: String,
    val sha256: String,
    val sizeInBytes: Long,
    val huggingFacePath: String? = null,
)

/**
 * Expands a DownloadFileSpec into its constituent download artifacts.
 * Primary file is always present; mmproj is included when all mmproj fields are set.
 */
fun DownloadFileSpec.requiredArtifacts(): List<DownloadArtifact> {
    val artifacts = mutableListOf(
        DownloadArtifact(
            remoteFileName = remoteFileName,
            localFileName = localFileName,
            sha256 = sha256,
            sizeInBytes = sizeInBytes,
            huggingFacePath = huggingFacePath,
        )
    )
    if (
        !mmprojRemoteFileName.isNullOrBlank() &&
        !mmprojLocalFileName.isNullOrBlank() &&
        !mmprojSha256.isNullOrBlank() &&
        mmprojSizeInBytes != null
    ) {
        artifacts += DownloadArtifact(
            remoteFileName = mmprojRemoteFileName,
            localFileName = mmprojLocalFileName,
            sha256 = mmprojSha256,
            sizeInBytes = mmprojSizeInBytes,
        )
    }
    return artifacts
}

/** Total size in bytes across all artifacts (primary + optional mmproj). */
fun DownloadFileSpec.totalArtifactSizeInBytes(): Long = requiredArtifacts().sumOf { it.sizeInBytes }
