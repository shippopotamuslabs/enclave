package com.browntowndev.enclave.domain.port.update

import com.browntowndev.enclave.domain.model.update.LatestRelease

/**
 * Reads the newest published release metadata from GitHub Releases.
 *
 * Failure-quiet by contract: returns `null` on ANY failure (network error,
 * non-2xx status, malformed JSON, missing field). Callers treat `null` as
 * "no update information" and must never surface the failure to the user.
 */
interface UpdateMetadataPort {
    suspend fun fetchLatestRelease(): LatestRelease?
}
