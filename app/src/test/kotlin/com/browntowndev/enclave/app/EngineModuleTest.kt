package com.browntowndev.enclave.app

import com.browntowndev.enclave.core.testing.FakeActiveModelProvider
import com.browntowndev.enclave.core.testing.FakeEmbeddingEngine
import com.browntowndev.enclave.core.testing.FakeLocalModelRepository
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.core.testing.FakeToolExecutor
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.feature.inference.ConversationManagerImpl
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * Tests for EngineModule model provider behavior.
 * These tests verify fixes for Bug #5 (model registration crash on missing models).
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34], application = android.app.Application::class)
class EngineModuleTest {
    /**
     * Verify that ConversationManager throws when no active configuration is registered.
     * This replaces the old "model registry returns null" test with the new
     * ActiveModelProviderPort / LocalModelRepositoryPort logic.
     */
    @Test
    fun `conversation manager throws when no active configuration exists`() =
        runTest {
            val context = RuntimeEnvironment.getApplication()
            // FakeActiveModelProvider has no configuration, so lookups return null.
            val manager =
                ConversationManagerImpl(
                    context,
                    FakeLocalModelRepository(),
                    FakeActiveModelProvider(),
                    FakeLoggingPort(),
                    FakeToolExecutor(),
                    FakeEmbeddingEngine(),
                )

            assertThrows<IllegalStateException> {
                manager.getConversation(ModelType.MAIN, null)
            }
        }

    /**
     * Verify that ModelType enum has all expected pipeline model types.
     */
    @Test
    fun `model type enum has all pipeline models`() {
        // Verify pipeline model types exist
        assertEquals("draft_one", ModelType.DRAFT_ONE.apiValue)
        assertEquals("draft_two", ModelType.DRAFT_TWO.apiValue)
        assertEquals("main", ModelType.MAIN.apiValue)
        assertEquals("fast", ModelType.FAST.apiValue)
        assertEquals("thinking", ModelType.THINKING.apiValue)
        assertEquals("vision", ModelType.VISION.apiValue)
    }

    /**
     * Verify ModelType.fromApiValue returns correct types.
     */
    @Test
    fun `model type from api value works correctly`() {
        assertEquals(ModelType.DRAFT_ONE, ModelType.fromApiValue("draft_one"))
        assertEquals(ModelType.DRAFT_TWO, ModelType.fromApiValue("draft_two"))
        assertEquals(ModelType.MAIN, ModelType.fromApiValue("main"))
        assertEquals(ModelType.FAST, ModelType.fromApiValue("fast"))
    }

    /**
     * Verify ModelType defaults to MAIN for unknown values.
     */
    @Test
    fun `model type defaults to main for unknown values`() {
        assertEquals(ModelType.MAIN, ModelType.fromApiValue("unknown"))
        assertEquals(ModelType.MAIN, ModelType.fromApiValue(""))
    }
}
