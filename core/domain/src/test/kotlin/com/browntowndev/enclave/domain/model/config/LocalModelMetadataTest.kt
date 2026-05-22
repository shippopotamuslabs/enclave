package com.browntowndev.enclave.domain.model.config

import com.browntowndev.enclave.domain.model.inference.ModelFileFormat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LocalModelMetadataTest {
    @Test
    fun `SCN-DOMAIN-001 local model metadata holds asset display name`() {
        val metadata =
            LocalModelMetadata(
                id = LocalModelId("model-1"),
                displayName = "Qwen 3.5 2B",
                huggingFaceModelName = "Jackrong/Qwen3.5-2B-Claude-4.6-Opus-Reasoning-Distilled-GGUF",
                remoteFileName = "model.gguf",
                localFileName = "model.gguf",
                sha256 = "sha",
                sizeInBytes = 100L,
                modelFileFormat = ModelFileFormat.GGUF,
            )

        assertEquals("Qwen 3.5 2B", metadata.displayName)
        assertEquals(
            "Jackrong/Qwen3.5-2B-Claude-4.6-Opus-Reasoning-Distilled-GGUF",
            metadata.huggingFaceModelName,
        )
    }
}
