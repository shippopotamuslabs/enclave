package com.browntowndev.pocketcrew.domain.model.config

import com.browntowndev.pocketcrew.domain.model.download.DownloadSource
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat

data class LocalModelMetadata(
    val id: LocalModelId,
    val huggingFaceModelName: String,
    val huggingFacePath: String? = null,
    val remoteFileName: String,
    val localFileName: String,
    val sha256: String,
    val sizeInBytes: Long,
    val modelFileFormat: ModelFileFormat,
    val source: DownloadSource = DownloadSource.HUGGING_FACE,
    val utilityType: UtilityType? = null,
    val isMultimodal: Boolean = false,
    val mmprojRemoteFileName: String? = null,
    val mmprojLocalFileName: String? = null,
    val mmprojSha256: String? = null,
    val mmprojSizeInBytes: Long? = null,
)

data class LocalModelArtifact(
    val remoteFileName: String,
    val localFileName: String,
    val sha256: String,
    val sizeInBytes: Long,
    val huggingFacePath: String? = null,
)

fun LocalModelMetadata.requiredArtifacts(): List<LocalModelArtifact> {
    val artifacts = mutableListOf(
        LocalModelArtifact(
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
        artifacts += LocalModelArtifact(
            remoteFileName = mmprojRemoteFileName,
            localFileName = mmprojLocalFileName,
            sha256 = mmprojSha256,
            sizeInBytes = mmprojSizeInBytes,
        )
    }
    return artifacts
}

fun LocalModelMetadata.totalArtifactSizeInBytes(): Long = requiredArtifacts().sumOf { it.sizeInBytes }
