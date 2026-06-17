package com.browntowndev.enclave.core.data.download.remote

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DownloadHttpErrorsTest {
    @Test
    fun isNonRetryable_returnsTrueFor404() {
        assertTrue(DownloadHttpErrors.isNonRetryable("HTTP 404: "))
    }

    @Test
    fun isNonRetryable_returnsTrueFor403() {
        assertTrue(DownloadHttpErrors.isNonRetryable("HTTP 403: Forbidden"))
    }

    @Test
    fun isNonRetryable_returnsFalseFor408And429() {
        assertFalse(DownloadHttpErrors.isNonRetryable("HTTP 408: Request Timeout"))
        assertFalse(DownloadHttpErrors.isNonRetryable("HTTP 429: Too Many Requests"))
    }

    @Test
    fun isNonRetryable_returnsFalseFor5xxAndNull() {
        assertFalse(DownloadHttpErrors.isNonRetryable("HTTP 503: Service Unavailable"))
        assertFalse(DownloadHttpErrors.isNonRetryable(null))
        assertFalse(DownloadHttpErrors.isNonRetryable("Connection reset"))
    }
}
