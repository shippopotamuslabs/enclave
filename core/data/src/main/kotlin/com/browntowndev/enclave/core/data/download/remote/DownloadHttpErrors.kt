package com.browntowndev.enclave.core.data.download.remote

/**
 * Classifies HTTP download failures that should not be retried by WorkManager.
 * Client errors such as 404 indicate a bad catalog URL, not a transient network issue.
 */
object DownloadHttpErrors {
    private val HTTP_STATUS_PATTERN = Regex("""HTTP (\d{3})""")
    private const val HTTP_CLIENT_ERROR_MIN = 400
    private const val HTTP_CLIENT_ERROR_MAX = 499

    fun isNonRetryable(message: String?): Boolean {
        val code =
            message
                ?.let {
                    HTTP_STATUS_PATTERN
                        .find(it)
                        ?.groupValues
                        ?.getOrNull(1)
                        ?.toIntOrNull()
                }
                ?: return false
        return code in HTTP_CLIENT_ERROR_MIN..HTTP_CLIENT_ERROR_MAX && code !in RETRYABLE_CLIENT_ERROR_CODES
    }

    private val RETRYABLE_CLIENT_ERROR_CODES = setOf(408, 429)
}
