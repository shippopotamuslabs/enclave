package com.browntowndev.pocketcrew.app

import android.content.Context
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.inference.ToolExecutorPort
import com.browntowndev.pocketcrew.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.pocketcrew.domain.port.repository.LocalModelRepositoryPort
import com.browntowndev.pocketcrew.feature.inference.ConversationManagerImpl
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Tests for EngineModule model provider behavior.
 * These tests verify fixes for Bug #5 (model registration crash on missing models).
 */
class EngineModuleTest {
    /**
     * Verify that ConversationManager throws when no active configuration is registered.
     * This replaces the old "model registry returns null" test with the new
     * ActiveModelProviderPort / LocalModelRepositoryPort logic.
     */
    @Test
    fun `conversation manager throws when no active configuration exists`() =
        runTest {
            val context = mockk<Context>(relaxed = true)
            val localModelRepository = mockk<LocalModelRepositoryPort>()
            val activeModelProvider = mockk<ActiveModelProviderPort>()
            val loggingPort = mockk<LoggingPort>(relaxed = true)
            val toolExecutor = mockk<ToolExecutorPort>(relaxed = true)

            coEvery { activeModelProvider.getActiveConfiguration(ModelType.MAIN) } returns null

            val manager = ConversationManagerImpl(context, localModelRepository, activeModelProvider, loggingPort, toolExecutor)

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
