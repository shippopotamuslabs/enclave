package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.domain.model.chat.ChatModelOverride
import com.browntowndev.enclave.domain.model.chat.ChatResponseMode
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.jvm.javaMethod

/**
 * RED tests for ChatResponseMode inference dispatch invariants (S06, MD01, E01 adjacent).
 *
 * Verifies the type-system invariants of [ChatResponseMode] and the
 * [ChatUseCases.generateChatResponse] signature change.
 */
class ChatResponseModeInferenceTest {
    @Nested
    @DisplayName("S06 — ChatResponseMode.Other carries override; slot variants do not")
    inner class OverrideCarrierInvariants {
        @Test
        @DisplayName("S06 — ChatResponseMode.Other carries the model override")
        fun s06_otherCarriesOverride() {
            val override =
                ChatModelOverride.Api(
                    configId = ApiModelConfigurationId("api-grok-beta"),
                    displayName = "Grok Beta",
                )
            val mode = ChatResponseMode.Other(modelOverride = override)

            assertSame(override, mode.modelOverride)
            assertEquals("Grok Beta", mode.modelOverride.displayName)
        }

        @Test
        @DisplayName("S06 — ChatResponseMode.Fast is a data object with no override field")
        fun s06_fastHasNoOverrideField() {
            val mode = ChatResponseMode.Fast
            val memberNames = mode::class.members.map { it.name }.toSet()
            assertFalse("modelOverride" in memberNames)
        }

        @Test
        @DisplayName("S06 — ChatResponseMode.Thinking is a data object with no override field")
        fun s06_thinkingHasNoOverrideField() {
            val mode = ChatResponseMode.Thinking
            val memberNames = mode::class.members.map { it.name }.toSet()
            assertFalse("modelOverride" in memberNames)
        }

        @Test
        @DisplayName("S06 — ChatResponseMode.Council is a data object with no override field")
        fun s06_councilHasNoOverrideField() {
            val mode = ChatResponseMode.Council
            val memberNames = mode::class.members.map { it.name }.toSet()
            assertFalse("modelOverride" in memberNames)
        }
    }

    @Nested
    @DisplayName("MD01 — Override is only on Other; FAST-slot fallback must fail")
    inner class MutationDefense {
        @Test
        @DisplayName("MD01 — Only ChatResponseMode.Other has modelOverride constructor parameter")
        fun md01_onlyOtherHasOverrideParam() {
            val otherClass: KClass<*> = ChatResponseMode.Other::class
            val otherParamNames = otherClass.constructors.flatMap { it.parameters.map { p -> p.name } }.toSet()
            assertTrue("modelOverride" in otherParamNames) {
                "ChatResponseMode.Other must have a modelOverride constructor parameter"
            }

            val slotClasses =
                setOf(
                    ChatResponseMode.Fast::class,
                    ChatResponseMode.Thinking::class,
                    ChatResponseMode.Council::class,
                )
            slotClasses.forEach { slotClass ->
                val paramNames = slotClass.constructors.flatMap { it.parameters.map { p -> p.name } }.toSet()
                assertFalse("modelOverride" in paramNames) {
                    "${slotClass.simpleName} must NOT have a modelOverride parameter"
                }
            }
        }

        @Test
        @DisplayName("MD01 — Local override configId is preserved in ChatResponseMode.Other")
        fun md01_localOverrideConfigIdPreserved() {
            val override =
                ChatModelOverride.Local(
                    configId = LocalModelConfigurationId("local-qwen-preset"),
                    displayName = "Qwen 3 8B Fast",
                )
            val mode = ChatResponseMode.Other(modelOverride = override)

            assertEquals(
                LocalModelConfigurationId("local-qwen-preset"),
                (mode.modelOverride as ChatModelOverride.Local).configId,
            )
        }

        @Test
        @DisplayName("MD01 — generateChatResponse signature takes responseMode: ChatResponseMode, not mode: Mode")
        fun md01_generateChatResponseSignatureChanged() {
            val generateMethod =
                ChatUseCases::class
                    .memberFunctions
                    .firstOrNull { it.name == "generateChatResponse" }
                    ?: error("ChatUseCases must declare generateChatResponse")

            val paramNames = generateMethod.parameters.map { it.name }
            assertTrue("responseMode" in paramNames) {
                "generateChatResponse must have a 'responseMode' parameter"
            }
            assertFalse("mode" in paramNames) {
                "generateChatResponse must NOT have a 'mode' parameter"
            }
        }
    }

    @Nested
    @DisplayName("E01 adjacent — ChatResponseMode sealed interface structure")
    inner class SealedInterfaceStructure {
        @Test
        @DisplayName("E01 — ChatResponseMode has exactly four variants")
        fun e01_exactlyFourVariants() {
            val variants: List<KClass<out ChatResponseMode>> = ChatResponseMode::class.sealedSubclasses
            assertEquals(4, variants.size)
        }

        @Test
        @DisplayName("E01 — ChatResponseMode variants: Fast, Thinking, Council, Other")
        fun e01_variantNames() {
            val variantNames =
                ChatResponseMode::class
                    .sealedSubclasses
                    .map { it.simpleName }
                    .toSet()
            assertEquals(
                setOf("Fast", "Thinking", "Council", "Other"),
                variantNames,
            )
        }
    }
}
