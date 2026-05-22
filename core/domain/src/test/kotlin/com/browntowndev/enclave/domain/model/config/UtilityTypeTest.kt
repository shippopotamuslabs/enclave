package com.browntowndev.enclave.domain.model.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UtilityTypeTest {
    @Test
    fun `displayName returns user-friendly labels`() {
        assertEquals("Speech Recognition", UtilityType.WHISPER.displayName())
        assertEquals("Embeddings", UtilityType.ONNX_MODEL.displayName())
        assertEquals("Embedding Vocabulary", UtilityType.ONNX_VOCAB.displayName())
        assertEquals("Utility Model", UtilityType.ONNX.displayName())
    }
}
