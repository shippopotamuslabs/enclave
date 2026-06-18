package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.model.config.OpenRouterDataCollectionPolicy
import com.browntowndev.enclave.domain.model.config.OpenRouterProviderSort
import com.browntowndev.enclave.domain.model.config.OpenRouterRoutingConfiguration
import com.openai.models.chat.completions.ChatCompletionCreateParams
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OpenRouterRoutingSupportTest {
    @Test
    fun `applyToChatCompletionBuilder writes provider routing body without clearing other properties`() {
        val builder =
            ChatCompletionCreateParams
                .builder()
                .model("anthropic/claude-sonnet-4")
                .addMessage(
                    com.openai.models.chat.completions.ChatCompletionMessageParam.ofUser(
                        com.openai.models.chat.completions.ChatCompletionUserMessageParam
                            .builder()
                            .content("hello")
                            .build(),
                    ),
                ).putAdditionalBodyProperty("reasoning", com.openai.core.JsonValue.from(mapOf("exclude" to false)))

        OpenRouterRoutingSupport.applyToChatCompletionBuilder(
            builder,
            OpenRouterRoutingConfiguration(
                providerSort = OpenRouterProviderSort.LATENCY,
                allowFallbacks = false,
                requireParameters = true,
                dataCollectionPolicy = OpenRouterDataCollectionPolicy.DENY,
                zeroDataRetention = true,
            ),
        )

        val params = builder.build()
        val body = params._additionalBodyProperties()
        assertTrue(body.containsKey("reasoning"), "Existing body properties must not be cleared")
        assertTrue(body.containsKey("provider"), "Provider routing must be present")
        val provider = body["provider"]!!.convert(Map::class.javaObjectType) as Map<*, *>
        assertEquals("latency", provider["sort"])
        assertEquals(false, provider["allow_fallbacks"])
        assertEquals(true, provider["require_parameters"])
        assertEquals("deny", provider["data_collection"])
        assertEquals(true, provider["zdr"])
    }

    @Test
    @org.junit.jupiter.api.DisplayName("SCN-SERIALIZE-001")
    fun scnSerialize001_openRouterRoutingSupportSerializesPreferredProviderToOrderProperty() {
        val builder =
            ChatCompletionCreateParams
                .builder()
                .model("anthropic/claude-sonnet-4")
                .addMessage(
                    com.openai.models.chat.completions.ChatCompletionMessageParam.ofUser(
                        com.openai.models.chat.completions.ChatCompletionUserMessageParam
                            .builder()
                            .content("hello")
                            .build(),
                    ),
                )

        OpenRouterRoutingSupport.applyToChatCompletionBuilder(
            builder,
            OpenRouterRoutingConfiguration(
                providerSort = OpenRouterProviderSort.THROUGHPUT,
                allowFallbacks = false,
                requireParameters = false,
                dataCollectionPolicy = OpenRouterDataCollectionPolicy.DENY,
                zeroDataRetention = false,
                preferredProvider = "deepinfra",
            ),
        )

        val params = builder.build()
        val body = params._additionalBodyProperties()
        assertTrue(body.containsKey("provider"), "Provider routing must be present")
        val provider = body["provider"]!!.convert(Map::class.javaObjectType) as Map<*, *>
        assertEquals("throughput", provider["sort"])
        assertEquals(false, provider["allow_fallbacks"])
        assertEquals(false, provider["require_parameters"])
        assertEquals("deny", provider["data_collection"])
        assertEquals(false, provider["zdr"])
        assertEquals(listOf("deepinfra"), provider["order"])
    }

    @Test
    @org.junit.jupiter.api.DisplayName("SCN-MUTATION-001")
    fun scnMutation001_customTargetProviderSerializationDefenseAgainstHardcoding() {
        val builder =
            ChatCompletionCreateParams
                .builder()
                .model("anthropic/claude-sonnet-4")
                .addMessage(
                    com.openai.models.chat.completions.ChatCompletionMessageParam.ofUser(
                        com.openai.models.chat.completions.ChatCompletionUserMessageParam
                            .builder()
                            .content("hello")
                            .build(),
                    ),
                )

        OpenRouterRoutingSupport.applyToChatCompletionBuilder(
            builder,
            OpenRouterRoutingConfiguration(
                preferredProvider = "random-unanticipated-provider-slug",
            ),
        )

        val params = builder.build()
        val body = params._additionalBodyProperties()
        assertTrue(body.containsKey("provider"), "Provider routing must be present")
        val provider = body["provider"]!!.convert(Map::class.javaObjectType) as Map<*, *>
        assertEquals(listOf("random-unanticipated-provider-slug"), provider["order"])
    }

    @Test
    fun scnSerialize002_openRouterRoutingSupportNormalizesOptionsWhenPreferredProviderAndNoFallbacks() {
        val builder =
            ChatCompletionCreateParams
                .builder()
                .model("anthropic/claude-sonnet-4")
                .addMessage(
                    com.openai.models.chat.completions.ChatCompletionMessageParam.ofUser(
                        com.openai.models.chat.completions.ChatCompletionUserMessageParam
                            .builder()
                            .content("hello")
                            .build(),
                    ),
                )

        OpenRouterRoutingSupport.applyToChatCompletionBuilder(
            builder,
            OpenRouterRoutingConfiguration(
                providerSort = OpenRouterProviderSort.LATENCY,
                allowFallbacks = false,
                requireParameters = true,
                dataCollectionPolicy = OpenRouterDataCollectionPolicy.ALLOW,
                zeroDataRetention = true,
                preferredProvider = "deepinfra",
            ),
        )

        val params = builder.build()
        val body = params._additionalBodyProperties()
        assertTrue(body.containsKey("provider"), "Provider routing must be present")
        val provider = body["provider"]!!.convert(Map::class.javaObjectType) as Map<*, *>
        assertEquals("throughput", provider["sort"])
        assertEquals(false, provider["allow_fallbacks"])
        assertEquals(false, provider["require_parameters"])
        assertEquals("deny", provider["data_collection"])
        assertEquals(false, provider["zdr"])
        assertEquals(listOf("deepinfra"), provider["order"])
    }
}