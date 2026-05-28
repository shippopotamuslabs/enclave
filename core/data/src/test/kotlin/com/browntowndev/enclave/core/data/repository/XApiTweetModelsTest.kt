package com.browntowndev.enclave.core.data.repository

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

class XApiTweetModelsTest {
    @Test
    fun `shouldFallbackTimelineToSearch does not trigger on generic HTTP 400`() {
        val error = IOException("X request failed with HTTP 400: bad request")
        assertFalse(shouldFallbackTimelineToSearch(error))
    }

    @Test
    fun `shouldFallbackTimelineToSearch triggers on HTTP 403`() {
        val error = IOException("X request failed with HTTP 403: Forbidden")
        assertTrue(shouldFallbackTimelineToSearch(error))
    }

    @Test
    fun `isInvalidPaginationTokenError detects pagination 400`() {
        val error =
            IOException(
                "X request failed with HTTP 400: {\"errors\":[{\"message\":\"Invalid pagination_token\"}]}",
            )
        assertTrue(isInvalidPaginationTokenError(error))
    }

    @Test
    fun `sanitizePaginationToken strips quotes`() {
        assertEquals("abc123", sanitizePaginationToken("\"abc123\""))
    }
}
