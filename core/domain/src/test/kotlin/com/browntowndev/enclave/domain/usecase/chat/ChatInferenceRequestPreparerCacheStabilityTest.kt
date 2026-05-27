package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.config.ActiveModelConfiguration
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.inference.ModelFileFormat
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.inference.ToolDisclosureMode
import com.browntowndev.enclave.domain.port.inference.EmbeddingEnginePort
import com.browntowndev.enclave.domain.port.inference.MonotonicToolActivationPort
import com.browntowndev.enclave.domain.port.inference.SearchIntentClassifierPort
import com.browntowndev.enclave.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.enclave.domain.port.repository.MemoriesRepository
import com.browntowndev.enclave.domain.port.repository.MessageRepository
import com.browntowndev.enclave.domain.port.repository.SettingsData
import com.browntowndev.enclave.domain.port.repository.SettingsRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChatInferenceRequestPreparerCacheStabilityTest {
    // ---------------------------------------------------------------------------
    // Shared helpers
    // ---------------------------------------------------------------------------

    private fun apiModelConfig(): ActiveModelConfiguration =
        ActiveModelConfiguration(
            id = ApiModelConfigurationId("api-model"),
            isLocal = false,
            name = "API Model",
            modelId = "test-model",
            systemPrompt = "Be concise.",
            reasoningEffort = null,
            temperature = 0.7,
            topK = 40,
            topP = 0.95,
            maxTokens = 512,
            minP = 0.0,
            repetitionPenalty = 1.0,
            contextWindow = 8192,
            thinkingEnabled = false,
            isMultimodal = false,
        )

    private fun localModelConfig(): ActiveModelConfiguration =
        ActiveModelConfiguration(
            id = LocalModelConfigurationId("local-model"),
            isLocal = true,
            name = "Local Model",
            modelId = "local-test-model",
            systemPrompt = "Be concise.",
            reasoningEffort = null,
            temperature = 0.7,
            topK = 40,
            topP = 0.95,
            maxTokens = 512,
            minP = 0.0,
            repetitionPenalty = 1.0,
            contextWindow = 8192,
            thinkingEnabled = false,
            isMultimodal = false,
            localModelFormat = ModelFileFormat.GGUF,
        )

    private fun mockActiveModelProvider(): ActiveModelProviderPort =
        mockk {
            coEvery { getActiveConfiguration(ModelType.FAST) } returns apiModelConfig()
            coEvery { getActiveConfiguration(ModelType.VISION) } returns null
        }

    private fun mockLocalModelProvider(): ActiveModelProviderPort =
        mockk {
            coEvery { getActiveConfiguration(ModelType.FAST) } returns localModelConfig()
            coEvery { getActiveConfiguration(ModelType.VISION) } returns null
        }

    private fun mockSettingsRepository(settings: SettingsData): SettingsRepository =
        mockk {
            every { settingsFlow } returns flowOf(settings)
        }

    private fun mockMessageRepository(): MessageRepository =
        mockk {
            coEvery { getMessageById(any()) } returns mockk(relaxed = true)
            coEvery { resolveLatestImageBearingUserMessage(any(), any()) } returns null
        }

    private fun mockMemoriesRepository(): MemoriesRepository =
        mockk {
            coEvery { getCoreMemories() } returns emptyList()
            coEvery { searchMemories(any(), any(), any(), any()) } returns emptyList()
        }

    private fun mockEmbeddingEngine(): EmbeddingEnginePort =
        mockk {
            coEvery { getEmbedding(any()) } returns floatArrayOf(0.1f, 0.2f)
        }

    private fun neverSearchClassifier(): SearchIntentClassifierPort =
        object : SearchIntentClassifierPort {
            override fun requiresSearch(
                embedding: FloatArray,
                threshold: Float,
            ): Boolean = false

            override fun getSearchIntentProbability(embedding: FloatArray): Float = 0.0f
        }

    private fun buildPreparer(
        settings: SettingsData,
        monotonicToolActivation: MonotonicToolActivationPort,
        searchIntentClassifier: SearchIntentClassifierPort = neverSearchClassifier(),
        activeModelProvider: ActiveModelProviderPort = mockActiveModelProvider(),
    ): ChatInferenceRequestPreparer =
        ChatInferenceRequestPreparer(
            activeModelProvider = activeModelProvider,
            settingsRepository = mockSettingsRepository(settings),
            messageRepository = mockMessageRepository(),
            memoriesRepository = mockMemoriesRepository(),
            embeddingEnginePort = mockEmbeddingEngine(),
            promptComposer = SearchToolPromptComposer(),
            defaultModelRepository = mockk(relaxed = true),
            mediaProviderRepository = mockk(relaxed = true),
            mediaToolDefinitionFactory = mockk(relaxed = true),
            getProviderCapabilitiesUseCase = mockk(relaxed = true),
            loggingPort = mockk(relaxed = true),
            searchIntentClassifier = searchIntentClassifier,
            monotonicToolActivation = monotonicToolActivation,
        )

    private suspend fun invoke(
        preparer: ChatInferenceRequestPreparer,
        prompt: String,
        chatId: String = "chat-1",
    ) = preparer(
        prompt = prompt,
        chatId = ChatId(chatId),
        userMessageId = MessageId("user"),
        assistantMessageId = MessageId("assistant"),
        modelType = ModelType.FAST,
    )

    // ---------------------------------------------------------------------------
    // CSPC-04a — deferred tool never activated; system prompt catalog block identical
    // ---------------------------------------------------------------------------

    @Test
    fun CSPC_04a_deferred_tool_no_activation_catalog_bytes_identical_across_turns() =
        runTest {
            val store = InMemoryMonotonicToolActivationStore()
            val preparer =
                buildPreparer(
                    settings = SettingsData(searchEnabled = true, tavilyKeyPresent = true),
                    monotonicToolActivation = store,
                    activeModelProvider = mockLocalModelProvider(),
                )

            val result1 = invoke(preparer, "hello")
            val systemPrompt1 = result1.options.systemPrompt

            val result2 = invoke(preparer, "hello again")
            val systemPrompt2 = result2.options.systemPrompt

            assertTrue(
                systemPrompt1?.contains("- search_chat_history [deferred]:") == true,
                "Turn 1 system prompt should contain '- search_chat_history [deferred]:'",
            )
            assertTrue(
                systemPrompt2?.contains("- search_chat_history [deferred]:") == true,
                "Turn 2 system prompt should contain '- search_chat_history [deferred]:'",
            )
            assertEquals(
                systemPrompt1,
                systemPrompt2,
                "CSPC-04a: systemPrompt1 must equal systemPrompt2 across turns with no intent activation",
            )
        }

    // ---------------------------------------------------------------------------
    // CSPC-04b — intent classifier activates search_x_recent on turn 2 but not 1 or 3;
    //             monotonic store keeps catalog identical on turn 3
    // ---------------------------------------------------------------------------

    @Test
    fun CSPC_04b_deferred_tool_flips_to_intent_activated_turn2_catalog_stable_monotonic_union() =
        runTest {
            // Turn 1: requiresSearch=false; Turn 2: requiresSearch=true; Turn 3: requiresSearch=false
            // The classifier is called twice per turn when xSearch is configured:
            // once by ToolIntentActivationPolicy.resolve() and once by the xDeferred block.
            val classifier =
                ControlledSearchIntentClassifier(
                    ArrayDeque(listOf(false, false, true, true, false, false)),
                )
            val store = InMemoryMonotonicToolActivationStore()
            val preparer =
                buildPreparer(
                    settings =
                        SettingsData(
                            searchEnabled = true,
                            tavilyKeyPresent = true,
                            xSearchEnabled = true,
                            xApiKeyPresent = true,
                        ),
                    monotonicToolActivation = store,
                    searchIntentClassifier = classifier,
                    activeModelProvider = mockLocalModelProvider(),
                )

            val result1 = invoke(preparer, "plain text")
            val disclosureModeTurn1 =
                result1.options.toolCatalog
                    .single { it.name == "search_x_recent" }
                    .disclosureMode
            val systemPromptTurn1 = result1.options.systemPrompt

            assertEquals(
                ToolDisclosureMode.DISCLOSE_ON_DEMAND,
                disclosureModeTurn1,
                "CSPC-04b turn 1: search_x_recent should be DISCLOSE_ON_DEMAND",
            )

            val result2 = invoke(preparer, "breaking news x")
            val disclosureModeTurn2 =
                result2.options.toolCatalog
                    .single { it.name == "search_x_recent" }
                    .disclosureMode
            val systemPromptTurn2 = result2.options.systemPrompt

            assertEquals(
                ToolDisclosureMode.INTENT_ACTIVATED,
                disclosureModeTurn2,
                "CSPC-04b turn 2: search_x_recent should be INTENT_ACTIVATED after classifier fires",
            )

            val result3 = invoke(preparer, "plain text again")
            val disclosureModeTurn3 =
                result3.options.toolCatalog
                    .single { it.name == "search_x_recent" }
                    .disclosureMode
            val systemPromptTurn3 = result3.options.systemPrompt

            assertEquals(
                ToolDisclosureMode.INTENT_ACTIVATED,
                disclosureModeTurn3,
                "CSPC-04b turn 3: search_x_recent must remain INTENT_ACTIVATED (monotonic union)",
            )
            assertEquals(
                systemPromptTurn2,
                systemPromptTurn3,
                "CSPC-04b: systemPromptTurn2 must equal systemPromptTurn3 — catalog must not regress",
            )
        }

    // ---------------------------------------------------------------------------
    // CSPC-04c — runtime disclose_tool_schemas does NOT affect the preparer's catalog
    //             on the following turn
    // ---------------------------------------------------------------------------

    @Test
    fun CSPC_04c_runtime_disclose_tool_schemas_does_not_mutate_preparer_catalog_next_turn() =
        runTest {
            val store = InMemoryMonotonicToolActivationStore()
            val preparer =
                buildPreparer(
                    // webSearchConfigured=false so start_artifact is deferred
                    settings = SettingsData(searchEnabled = false),
                    monotonicToolActivation = store,
                    activeModelProvider = mockLocalModelProvider(),
                )

            val result1 = invoke(preparer, "what is the weather today", chatId = "chat-2")
            val systemPromptTurn1 = result1.options.systemPrompt

            assertTrue(
                systemPromptTurn1?.contains("- start_artifact [deferred]:") == true,
                "CSPC-04c turn 1: system prompt should contain '- start_artifact [deferred]:'",
            )

            // The InMemoryMonotonicToolActivationStore is NOT updated by disclose_tool_schemas
            // at runtime — only ChatInferenceRequestPreparer.invoke updates it via the intent
            // classifier. So turn 2 should see the same catalog as turn 1.

            val result2 = invoke(preparer, "tell me more", chatId = "chat-2")
            val systemPromptTurn2 = result2.options.systemPrompt

            assertTrue(
                systemPromptTurn2?.contains("- start_artifact [deferred]:") == true,
                "CSPC-04c turn 2: system prompt should still contain '- start_artifact [deferred]:'",
            )
            assertEquals(
                systemPromptTurn1,
                systemPromptTurn2,
                "CSPC-04c: systemPromptTurn1 must equal systemPromptTurn2 — no catalog drift without intent activation",
            )
        }

    // ---------------------------------------------------------------------------
    // CSPC-MUTDEF-01 — identity MonotonicToolActivationPort causes catalog to shrink on turn 3
    // ---------------------------------------------------------------------------

    @Test
    fun CSPC_MUTDEF_01_identity_monotonic_store_causes_catalog_regression_on_turn3() =
        runTest {
            // Turn 2: requiresSearch=true; Turn 3: requiresSearch=false
            // The classifier is called twice per turn when xSearch is configured:
            // once by ToolIntentActivationPolicy.resolve() and once by the xDeferred block.
            val classifier =
                ControlledSearchIntentClassifier(
                    ArrayDeque(listOf(true, true, false, false)),
                )
            val preparer =
                buildPreparer(
                    settings =
                        SettingsData(
                            searchEnabled = true,
                            tavilyKeyPresent = true,
                            xSearchEnabled = true,
                            xApiKeyPresent = true,
                        ),
                    monotonicToolActivation = IdentityMonotonicToolActivationPort,
                    searchIntentClassifier = classifier,
                    activeModelProvider = mockLocalModelProvider(),
                )

            val result2 = invoke(preparer, "breaking news x")
            val disclosureModeTurn2 =
                result2.options.toolCatalog
                    .single { it.name == "search_x_recent" }
                    .disclosureMode
            val systemPromptTurn2 = result2.options.systemPrompt

            assertEquals(
                ToolDisclosureMode.INTENT_ACTIVATED,
                disclosureModeTurn2,
                "CSPC-MUTDEF-01 turn 2: search_x_recent should be INTENT_ACTIVATED",
            )

            val result3 = invoke(preparer, "plain text")
            val disclosureModeTurn3 =
                result3.options.toolCatalog
                    .single { it.name == "search_x_recent" }
                    .disclosureMode
            val systemPromptTurn3 = result3.options.systemPrompt

            assertEquals(
                ToolDisclosureMode.DISCLOSE_ON_DEMAND,
                disclosureModeTurn3,
                "CSPC-MUTDEF-01 turn 3: identity stub returns DISCLOSE_ON_DEMAND (regressed — confirms stub is broken)",
            )
            assertNotEquals(
                systemPromptTurn2,
                systemPromptTurn3,
                "CSPC-MUTDEF-01: systemPromptTurn2 must NOT equal systemPromptTurn3 when using identity stub — proves stub is insufficient",
            )
        }

    // ---------------------------------------------------------------------------
    // Test helpers
    // ---------------------------------------------------------------------------

    /**
     * Controlled classifier that pops from a deque of booleans for requiresSearch.
     */
    private class ControlledSearchIntentClassifier(
        private val decisions: ArrayDeque<Boolean>,
    ) : SearchIntentClassifierPort {
        override fun requiresSearch(
            embedding: FloatArray,
            threshold: Float,
        ): Boolean = decisions.removeFirstOrNull() ?: false

        override fun getSearchIntentProbability(embedding: FloatArray): Float = if (decisions.firstOrNull() == true) 0.95f else 0.05f
    }

    /**
     * Identity stub — always returns [newNames] unchanged (does NOT accumulate).
     * Used by CSPC-MUTDEF-01 to prove the real store is required.
     */
    private object IdentityMonotonicToolActivationPort : MonotonicToolActivationPort {
        override fun union(
            chatId: ChatId,
            newNames: Set<String>,
        ): Set<String> = newNames

        override fun clearSession(chatId: ChatId) = Unit
    }
}
