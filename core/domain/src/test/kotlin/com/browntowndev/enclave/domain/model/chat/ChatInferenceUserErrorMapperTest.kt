package com.browntowndev.enclave.domain.model.chat

import com.browntowndev.enclave.domain.model.error.ActiveModelConfigurationDecryptException
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.inference.InferenceBusyException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class ChatInferenceUserErrorMapperTest {
    @Test
    fun `maps rate limit sdk dump with provider reset detail`() {
        val error =
            RuntimeException(
                "model turn failed (TRANSIENT): RateLimitException: 429: Weekly/Monthly Limit Exhausted. " +
                    "Your limit will reset at 2026-06-21 05:51:46 " +
                    "[exception=RateLimitException message=429: Weekly/Monthly Limit Exhausted. " +
                    "Your limit will reset at 2026-06-21 05:51:46 status=429 code=1310 " +
                    "body={code=1310, message=Weekly/Monthly Limit Exhausted. " +
                    "Your limit will reset at 2026-06-21 05:51:46}]",
                RuntimeException(
                    "exception=RateLimitException message=429: Weekly/Monthly Limit Exhausted. " +
                        "Your limit will reset at 2026-06-21 05:51:46 status=429 code=1310 " +
                        "body={code=1310, message=Weekly/Monthly Limit Exhausted. " +
                        "Your limit will reset at 2026-06-21 05:51:46}",
                ),
            )

        val mapped = ChatInferenceUserErrorMapper.map(error)

        assertTrue(mapped.title.contains("Weekly/Monthly Limit Exhausted"))
        assertTrue(mapped.title.contains("2026-06-21 05:51:46"))
        assertNull(mapped.detail)
        assertTrue(mapped.hint.orEmpty().contains("subscription plan"))
        assertEquals(ChatInferenceErrorCategory.RATE_LIMITED, mapped.category)
    }

    @Test
    fun `maps guardrail retry exhaustion with embedded provider message as title`() {
        val mapped =
            ChatInferenceUserErrorMapper.map(
                RuntimeException(
                    "Agent loop failed: Guardrail exceeded: model_api_retries_exhausted: " +
                        "RateLimitException: 429: Weekly/Monthly Limit Exhausted. " +
                        "Your limit will reset at 2026-06-21 05:51:46",
                ),
            )

        assertEquals(ChatInferenceErrorCategory.RATE_LIMITED, mapped.category)
        assertTrue(mapped.title.contains("Weekly/Monthly Limit Exhausted"))
        assertTrue(mapped.title.contains("2026-06-21 05:51:46"))
        assertTrue(mapped.hint.orEmpty().contains("subscription plan"))
        assertFalse(mapped.formatForMessage().contains("model_api_retries_exhausted"))
    }

    @Test
    fun `maps completeSingleTurn failed sdk dump with provider reset detail`() {
        val mapped =
            ChatInferenceUserErrorMapper.map(
                RuntimeException(
                    "completeSingleTurn failed: exception=RateLimitException " +
                        "message=429: Weekly/Monthly Limit Exhausted. " +
                        "Your limit will reset at 2026-06-21 05:51:46 status=429 code=1310 " +
                        "body={code=1310, message=Weekly/Monthly Limit Exhausted. " +
                        "Your limit will reset at 2026-06-21 05:51:46}",
                ),
            )

        assertEquals(ChatInferenceErrorCategory.RATE_LIMITED, mapped.category)
        assertTrue(mapped.title.contains("Weekly/Monthly Limit Exhausted"))
        assertTrue(mapped.title.contains("2026-06-21 05:51:46"))
    }

    @Test
    fun `maps model api retries exhausted without internal kind jargon`() {
        val mapped =
            ChatInferenceUserErrorMapper.map(
                RuntimeException("Agent loop failed: Guardrail exceeded: model_api_retries_exhausted"),
            )

        assertEquals("The model provider failed after several retry attempts.", mapped.title)
        assertTrue(mapped.hint.orEmpty().contains("API key"))
        assertEquals(ChatInferenceErrorCategory.PROVIDER_UNAVAILABLE, mapped.category)
        assertFalse(mapped.formatForMessage().contains("model_api_retries_exhausted"))
    }

    @ParameterizedTest
    @MethodSource("httpStatusCases")
    fun `maps http status codes to actionable categories`(
        rawMessage: String,
        expectedTitle: String,
        expectedCategory: ChatInferenceErrorCategory,
    ) {
        val mapped = ChatInferenceUserErrorMapper.map(RuntimeException(rawMessage))
        assertEquals(expectedTitle, mapped.title)
        assertEquals(expectedCategory, mapped.category)
    }

    @Test
    fun `maps auth failure when status only appears in metadata`() {
        val mapped =
            ChatInferenceUserErrorMapper.map(
                RuntimeException("exception=AuthException message=Unauthorized status=401 body={}"),
            )

        assertEquals("Unauthorized", mapped.title)
        assertEquals(ChatInferenceErrorCategory.AUTH_FAILURE, mapped.category)
    }

    @Test
    fun `redacts api key material from provider detail`() {
        val mapped =
            ChatInferenceUserErrorMapper.map(
                RuntimeException(
                    "HTTP 401: Incorrect API key provided: sk-proj-abcdefghijklmnopqrstuvwxyz123456",
                ),
            )

        assertEquals(ChatInferenceErrorCategory.AUTH_FAILURE, mapped.category)
        assertNull(mapped.detail)
        assertFalse(mapped.formatForMessage().contains("sk-proj"))
    }

    @Test
    fun `maps model api unavailable stream termination`() {
        val mapped =
            ChatInferenceUserErrorMapper.map(
                RuntimeException(
                    "Agent loop failed: Model API unavailable: stream ended unexpectedly (attempts=3)",
                ),
            )

        assertEquals("Your model provider is temporarily unavailable.", mapped.title)
        assertEquals(ChatInferenceErrorCategory.TRANSPORT_FAILURE, mapped.category)
        assertTrue(mapped.hint.orEmpty().contains("Try again"))
        assertFalse(mapped.formatForMessage().contains("attempts=3"))
    }

    @Test
    fun `maps bare model stream termination without provider names`() {
        val mapped =
            ChatInferenceUserErrorMapper.map(
                RuntimeException(
                    "Model stream ended unexpectedly without finish signal (provider=openai model=gpt-4)",
                ),
            )

        assertEquals(ChatInferenceErrorCategory.TRANSPORT_FAILURE, mapped.category)
        assertFalse(mapped.formatForMessage().contains("provider="))
        assertFalse(mapped.formatForMessage().contains("gpt-4"))
    }

    @Test
    fun `maps pipeline compaction ceiling to prompt too large category`() {
        val mapped =
            ChatInferenceUserErrorMapper.map(
                RuntimeException(
                    "Agent loop failed: Compaction failed (tier=pipeline): " +
                        "Projected prompt 120000 tokens exceeds hard ceiling 100000 tokens after compaction",
                ),
            )

        assertEquals(ChatInferenceUserErrorMapper.PROMPT_TOO_LARGE_TITLE, mapped.title)
        assertEquals(ChatInferenceErrorCategory.PROMPT_TOO_LARGE, mapped.category)
        assertTrue(ChatInferenceUserErrorMapper.isPromptTooLarge(mapped.formatForMessage()))
    }

    @Test
    fun `maps agent internal errors without leaking implementation detail`() {
        val mapped =
            ChatInferenceUserErrorMapper.map(
                RuntimeException(
                    "Agent loop failed: Invalid state transition from 'AwaitingModel' to 'ExecuteTools'",
                ),
            )

        assertEquals(ChatInferenceUserErrorMapper.GENERIC_TITLE, mapped.title)
        assertEquals(ChatInferenceErrorCategory.AGENT_INTERNAL, mapped.category)
        assertNull(mapped.detail)
        assertFalse(mapped.formatForMessage().contains("AwaitingModel"))
    }

    @Test
    fun `maps max tokens stop to token limit category`() {
        val mapped =
            ChatInferenceUserErrorMapper.map(
                RuntimeException("Model stopped before completion: max_tokens"),
            )

        assertEquals("The response hit the model token limit.", mapped.title)
        assertEquals(ChatInferenceErrorCategory.TOKEN_LIMIT, mapped.category)
    }

    @Test
    fun `maps bare compaction failed to agent internal category`() {
        val mapped =
            ChatInferenceUserErrorMapper.map(
                RuntimeException("Agent loop failed: Compaction failed"),
            )

        assertEquals(ChatInferenceErrorCategory.AGENT_INTERNAL, mapped.category)
        assertEquals(ChatInferenceUserErrorMapper.GENERIC_TITLE, mapped.title)
    }

    @Test
    fun `maps unrecognized guardrail with rate limit status`() {
        val mapped =
            ChatInferenceUserErrorMapper.map(
                RuntimeException(
                    "Guardrail exceeded: custom_policy status=429 message=429: Too many requests",
                ),
            )

        assertEquals(ChatInferenceErrorCategory.RATE_LIMITED, mapped.category)
        assertTrue(mapped.title.contains("Too many requests"))
    }

    @Test
    fun `collectMessages terminates on cyclic cause chain`() {
        val errorA = RuntimeException("limit reset at tomorrow")
        val errorB = RuntimeException("secondary", errorA)
        errorA.initCause(errorB)

        val mapped = ChatInferenceUserErrorMapper.map(errorA)

        assertTrue(mapped.title.contains("limit reset at tomorrow"))
    }

    @Test
    fun `maps max tool turns guardrail`() {
        val mapped =
            ChatInferenceUserErrorMapper.map(
                RuntimeException("Guardrail exceeded: max_tool_turns"),
            )

        assertEquals(ChatInferenceErrorCategory.AGENT_LIMIT, mapped.category)
        assertTrue(mapped.hint.orEmpty().contains("smaller steps"))
    }

    @Test
    fun `maps busy and decrypt failures via typed exceptions`() {
        val busy = ChatInferenceUserErrorMapper.map(InferenceBusyException())
        assertEquals(ChatInferenceErrorCategory.BUSY, busy.category)
        assertTrue(busy.title.contains("in progress"))

        val decrypt =
            ChatInferenceUserErrorMapper.map(
                ActiveModelConfigurationDecryptException(
                    modelType = ModelType.FAST,
                ),
            )
        assertEquals(ChatInferenceErrorCategory.DECRYPT_FAILURE, decrypt.category)
        assertEquals(ActiveModelConfigurationDecryptException.USER_MESSAGE, decrypt.title)
    }

    @Test
    fun `sanitizes custom busy exception messages`() {
        val mapped =
            ChatInferenceUserErrorMapper.map(
                InferenceBusyException("response was killed by Android due to thermal shutdown"),
            )

        assertEquals(ChatInferenceErrorCategory.BUSY, mapped.category)
        assertTrue(mapped.title.contains("thermal shutdown"))
    }

    @Test
    fun `unknown errors avoid raw exception class names`() {
        val mapped = ChatInferenceUserErrorMapper.map(RuntimeException())

        assertEquals(ChatInferenceUserErrorMapper.GENERIC_TITLE, mapped.title)
        assertFalse(mapped.formatForMessage().contains("RuntimeException"))
    }

    @Test
    fun `blank message maps to generic error`() {
        val mapped = ChatInferenceUserErrorMapper.mapFromMessage("   ")
        assertEquals(ChatInferenceUserErrorMapper.GENERIC_TITLE, mapped.title)
    }

    @Test
    fun `stack trace content is not surfaced to users`() {
        val mapped =
            ChatInferenceUserErrorMapper.map(
                RuntimeException(
                    "Agent loop failed: Guardrail exceeded: model_api_retries_exhausted\n" +
                        "\tat com.example.Service.stream(Service.kt:973)\n" +
                        "Caused by: java.lang.IllegalStateException: hidden",
                ),
            )

        assertFalse(mapped.formatForMessage().contains("Service.kt"))
        assertFalse(mapped.formatForMessage().contains("IllegalStateException"))
    }

    @Test
    fun `detects prompt too large from legacy wrapped error text`() {
        val legacy =
            "Sorry, something went wrong. Please try again.\n\n" +
                "Error: Agent loop failed: tier=pipeline, " +
                "reason=Projected prompt exceeds hard ceiling"

        assertTrue(ChatInferenceUserErrorMapper.isPromptTooLarge(legacy))
    }

    @ParameterizedTest
    @MethodSource("agentProducerMessageCases")
    fun `maps agent producer message formats`(
        producerMessage: String,
        expectedCategory: ChatInferenceErrorCategory,
    ) {
        val mapped = ChatInferenceUserErrorMapper.map(RuntimeException(producerMessage))
        assertEquals(expectedCategory, mapped.category)
    }

    companion object {
        @JvmStatic
        fun httpStatusCases(): List<Arguments> =
            listOf(
                Arguments.of(
                    "HTTP 401: Invalid API key",
                    "Invalid API key",
                    ChatInferenceErrorCategory.AUTH_FAILURE,
                ),
                Arguments.of(
                    "HTTP 403: Permission denied",
                    "Permission denied",
                    ChatInferenceErrorCategory.ACCESS_DENIED,
                ),
                Arguments.of(
                    "HTTP 404: Not Found",
                    "Not Found",
                    ChatInferenceErrorCategory.ENDPOINT_NOT_FOUND,
                ),
                Arguments.of(
                    "HTTP 400: Invalid tool call in messages",
                    "Invalid tool call in messages",
                    ChatInferenceErrorCategory.INVALID_REQUEST,
                ),
                Arguments.of(
                    "HTTP 408: Request Timeout",
                    "Request Timeout",
                    ChatInferenceErrorCategory.TRANSPORT_FAILURE,
                ),
                Arguments.of(
                    "HTTP 502: Bad Gateway",
                    "Bad Gateway",
                    ChatInferenceErrorCategory.TRANSPORT_FAILURE,
                ),
                Arguments.of(
                    "HTTP 503: Service Unavailable",
                    "Service Unavailable",
                    ChatInferenceErrorCategory.TRANSPORT_FAILURE,
                ),
            )

        @JvmStatic
        fun agentProducerMessageCases(): List<Arguments> =
            listOf(
                Arguments.of(
                    "Guardrail exceeded: model_api_retries_exhausted",
                    ChatInferenceErrorCategory.PROVIDER_UNAVAILABLE,
                ),
                Arguments.of(
                    "Compaction failed (tier=pipeline): Projected prompt 1 tokens exceeds hard ceiling 1 tokens",
                    ChatInferenceErrorCategory.PROMPT_TOO_LARGE,
                ),
                Arguments.of(
                    "Model API unavailable: HTTP 401: bad key (attempts=2)",
                    ChatInferenceErrorCategory.AUTH_FAILURE,
                ),
                Arguments.of(
                    "Model stopped before completion: max_tokens",
                    ChatInferenceErrorCategory.TOKEN_LIMIT,
                ),
                Arguments.of(
                    "Guardrail exceeded: max_tool_turns",
                    ChatInferenceErrorCategory.AGENT_LIMIT,
                ),
                Arguments.of(
                    "Invalid state transition from 'a' to 'b'",
                    ChatInferenceErrorCategory.AGENT_INTERNAL,
                ),
            )
    }
}