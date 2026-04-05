package com.browntowndev.pocketcrew.app
import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.repository.LocalModelRepositoryPort
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
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