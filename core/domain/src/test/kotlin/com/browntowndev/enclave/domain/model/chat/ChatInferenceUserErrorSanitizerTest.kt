package com.browntowndev.enclave.domain.model.chat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class ChatInferenceUserErrorSanitizerTest {
    @Test
    fun `sanitizeProviderDetail preserves helpful provider copy without secrets`() {
        val detail =
            ChatInferenceUserErrorSanitizer.sanitizeProviderDetail(
                "429: Weekly limit exhausted. Your limit will reset at 2026-06-21 05:51:46",
            )

        assertEquals(
            "429: Weekly limit exhausted. Your limit will reset at 2026-06-21 05:51:46",
            detail,
        )
    }

    @Test
    fun `sanitizeProviderDetail preserves at capacity phrasing`() {
        val detail =
            ChatInferenceUserErrorSanitizer.sanitizeProviderDetail(
                "at capacity, try again later",
            )

        assertEquals("at capacity, try again later", detail)
    }

    @Test
    fun `sanitizeProviderDetail preserves disk and risk wording`() {
        val detail =
            ChatInferenceUserErrorSanitizer.sanitizeProviderDetail(
                "disk-full risk detected on upstream node",
            )

        assertEquals("disk-full risk detected on upstream node", detail)
    }

    @ParameterizedTest
    @MethodSource("secretCases")
    fun `sanitizeProviderDetail redacts secret material`(raw: String) {
        assertNull(ChatInferenceUserErrorSanitizer.sanitizeProviderDetail(raw))
    }

    @Test
    fun `sanitizeProviderDetail redacts JSON api_key field`() {
        val detail =
            ChatInferenceUserErrorSanitizer.sanitizeProviderDetail(
                """Request failed with body={"api_key":"super-secret-value","code":401}""",
            )

        assertNull(detail)
    }

    @Test
    fun `sanitizeProviderDetail redacts basic auth urls`() {
        assertNull(
            ChatInferenceUserErrorSanitizer.sanitizeProviderDetail(
                "Upstream failed at https://user:password@api.example.com/v1/chat",
            ),
        )
    }

    @Test
    fun `looksLikeStackTrace matches java frames only`() {
        assertTrue(
            ChatInferenceUserErrorSanitizer.looksLikeStackTrace(
                "failure\n\tat com.example.Service.stream(Service.kt:973)",
            ),
        )
        assertFalse(
            ChatInferenceUserErrorSanitizer.looksLikeStackTrace(
                "at capacity, try again later",
            ),
        )
    }

    @Test
    fun `sanitizeUserFacingTitle falls back for stack traces`() {
        val title =
            ChatInferenceUserErrorSanitizer.sanitizeUserFacingTitle(
                candidate = "at com.example.Service.stream(Service.kt:12)",
                fallback = "Something went wrong.",
            )

        assertEquals("Something went wrong.", title)
    }

    @Test
    fun `sanitizeProviderDetail strips sdk metadata before surfacing detail`() {
        val detail =
            ChatInferenceUserErrorSanitizer.sanitizeProviderDetail(
                """exception=RateLimitException message=429: Weekly limit exhausted status=429 body={code=1310}""",
            )

        assertEquals("429: Weekly limit exhausted", detail)
    }

    @Test
    fun `sanitizeProviderDetail preserves exact rate limit sdk dump from user report`() {
        val detail =
            ChatInferenceUserErrorSanitizer.sanitizeProviderDetail(
                "exception=RateLimitException message=429: Weekly/Monthly Limit Exhausted. " +
                    "Your limit will reset at 2026-06-21 05:51:46 status=429 code=1310 " +
                    "body={code=1310, message=Weekly/Monthly Limit Exhausted. " +
                    "Your limit will reset at 2026-06-21 05:51:46}",
            )

        assertEquals(
            "429: Weekly/Monthly Limit Exhausted. Your limit will reset at 2026-06-21 05:51:46",
            detail,
        )
    }

    companion object {
        @JvmStatic
        fun secretCases(): List<Arguments> =
            listOf(
                Arguments.of("HTTP 401: Incorrect API key provided: sk-proj-abcdefghijklmnopqrstuvwxyz123456"),
                Arguments.of("Invalid key AIzaSyD-example-key-material-1234567890"),
                Arguments.of("Authentication failed for xai-live-production-key-abcdef12"),
                Arguments.of("Bearer eyJhbGciOiJIUzI1NiJ9.payload.signature"),
                Arguments.of("api_key=super-secret-token-value"),
                Arguments.of("x-api-key: deadbeefcafebabe"),
                Arguments.of("Z.ai rejected key 0123456789abcdef0123456789abcdef.secretmaterialabcd"),
            )
    }
}