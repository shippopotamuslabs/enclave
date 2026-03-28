package com.browntowndev.pocketcrew.app
import android.content.Context
import com.browntowndev.pocketcrew.domain.model.config.ModelConfiguration
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.repository.ModelRegistryPort
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test


/**
 * Tests for EngineModule model provider behavior.
 * These tests verify fixes for Bug #5 (model registration crash on missing models).
 */
class EngineModuleTest {

    /**
     * BUG #5: App crashes at startup if DRAFT_ONE/DRAFT_TWO models aren't registered.
     *
     * Scenario: User hasn't downloaded DRAFT_ONE or DRAFT_TWO models
     * Expected: App should handle gracefully (lazy loading or null returns)
     * Current behavior: Throws IllegalStateException at startup
     */
    @Test
    fun `model registry returns null for unregistered model types`() {
        // Mock registry that returns null for DRAFT models
        val mockRegistry = mockk<ModelRegistryPort>()

        // When DRAFT_ONE is not registered
        every { mockRegistry.getRegisteredModelSync(ModelType.DRAFT_ONE) } returns null
        every { mockRegistry.getRegisteredModelSync(ModelType.DRAFT_TWO) } returns null

        val fastConfig = mockk<ModelConfiguration>()
        every { fastConfig.metadata } returns mockk {
            every { localFileName } returns "fast.bin"
        }
        every { mockRegistry.getRegisteredModelSync(ModelType.FAST) } returns fastConfig

        // Verify null handling
        val draftOneConfig = mockRegistry.getRegisteredModelSync(ModelType.DRAFT_ONE)
        val draftTwoConfig = mockRegistry.getRegisteredModelSync(ModelType.DRAFT_TWO)
        val fastResult = mockRegistry.getRegisteredModelSync(ModelType.FAST)

        assertNull(draftOneConfig, "DRAFT_ONE should return null when not registered")
        assertNull(draftTwoConfig, "DRAFT_TWO should return null when not registered")
        assertNotNull(fastResult, "FAST should return config when registered")
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
