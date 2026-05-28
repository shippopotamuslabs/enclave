package com.browntowndev.enclave.feature.chat.mapping

import com.browntowndev.enclave.core.testing.MainDispatcherRule
import com.browntowndev.enclave.domain.model.config.ActiveModelConfiguration
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.enclave.feature.chat.ChatModeUi
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MainDispatcherRule::class)
@DisplayName("PhotoAttachmentPolicyResolver")
class PhotoAttachmentPolicyResolverTest {
    private lateinit var activeModelProvider: ActiveModelProviderPort
    private lateinit var resolver: PhotoAttachmentPolicyResolver

    @BeforeEach
    fun setUp() {
        activeModelProvider = mockk()
        resolver = PhotoAttachmentPolicyResolver(activeModelProvider = activeModelProvider)
    }

    @Test
    @DisplayName("COUNCIL without API vision disables attachments")
    fun councilWithoutApiVisionClearsPolicy() =
        runTest {
            coEvery { activeModelProvider.getActiveConfiguration(ModelType.VISION) } returns null

            val policy = resolver.resolve(ChatModeUi.COUNCIL)

            assertFalse(policy.isEnabled)
            assertEquals(0, policy.maxItems)
            assertTrue(policy.disabledReason?.contains("Council") == true)
        }

    @Test
    @DisplayName("FAST to THINKING keeps attachments when vision-capable models are active")
    fun fastToThinkingKeepsAttachments() =
        runTest {
            val multimodalFast = visionCapableConfig(isLocal = true)
            coEvery { activeModelProvider.getActiveConfiguration(ModelType.FAST) } returns multimodalFast
            coEvery { activeModelProvider.getActiveConfiguration(ModelType.THINKING) } returns multimodalFast
            coEvery { activeModelProvider.getActiveConfiguration(ModelType.VISION) } returns null

            val fastPolicy = resolver.resolve(ChatModeUi.FAST)
            val thinkingPolicy = resolver.resolve(ChatModeUi.THINKING)

            assertTrue(fastPolicy.isEnabled)
            assertTrue(thinkingPolicy.isEnabled)
            assertEquals(1, fastPolicy.maxItems)
            assertEquals(1, thinkingPolicy.maxItems)
        }

    @Test
    @DisplayName("API Google vision allows large maxItems for trim decisions")
    fun apiGoogleVisionMaxItemsForTrim() =
        runTest {
            val googleVision =
                visionCapableConfig(
                    isLocal = false,
                    apiProvider = ApiProvider.GOOGLE,
                )
            coEvery { activeModelProvider.getActiveConfiguration(ModelType.FAST) } returns null
            coEvery { activeModelProvider.getActiveConfiguration(ModelType.THINKING) } returns null
            coEvery { activeModelProvider.getActiveConfiguration(ModelType.VISION) } returns googleVision

            val policy = resolver.resolve(ChatModeUi.FAST)

            assertTrue(policy.isEnabled)
            assertEquals(3600, policy.maxItems)
        }

    private fun visionCapableConfig(
        isLocal: Boolean,
        apiProvider: ApiProvider? = null,
    ): ActiveModelConfiguration =
        ActiveModelConfiguration(
            modelId = "vision-model",
            id = ApiModelConfigurationId("cfg"),
            isLocal = isLocal,
            name = "Vision",
            systemPrompt = null,
            temperature = 0.7,
            topK = 40,
            topP = 0.95,
            maxTokens = 512,
            minP = 0.0,
            repetitionPenalty = 1.1,
            contextWindow = 4096,
            thinkingEnabled = false,
            isMultimodal = true,
            apiProvider = apiProvider,
        )
}
