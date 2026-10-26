package com.browntowndev.enclave.domain.model.update

/**
 * Metadata for the newest published Enclave release, read from the stable
 * `latest.json` document on GitHub Releases (ENC-248 contract).
 *
 * [downloadUrl] is derived from [apkFileName] against the stable
 * `/releases/latest/download/` prefix, so callers never build URLs themselves.
 */
data class LatestRelease(
    val versionCode: Int,
    val versionName: String,
    val apkFileName: String,
    val downloadUrl: String,
)
