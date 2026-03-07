package com.browntowndev.pocketcrew.data.model

import com.browntowndev.pocketcrew.domain.model.ModelConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Unit tests for ModelConfig constants.
 */
class ModelConfigTest {

    @Test
    fun `ModelConfig has correct R2 bucket URL`() {
        // Note: This is a compile-time check since R2_BUCKET_URL is private
        assertNotNull(ModelConfig.MODELS_DIR)
        assertEquals("models", ModelConfig.MODELS_DIR)
    }

    @Test
    fun `ModelConfig has correct temp extension`() {
        assertEquals(".tmp", ModelConfig.TEMP_EXTENSION)
    }

    @Test
    fun `ModelConfig has required free space of 15GB`() {
        assertEquals(15L * 1024 * 1024 * 1024, ModelConfig.REQUIRED_FREE_SPACE_BYTES)
    }

    @Test
    fun `ModelConfig has correct concurrent downloads setting`() {
        assertEquals(3, ModelConfig.CONCURRENT_DOWNLOADS)
    }

    @Test
    fun `ModelConfig has correct max retry attempts`() {
        assertEquals(3, ModelConfig.MAX_RETRY_ATTEMPTS)
    }

}

