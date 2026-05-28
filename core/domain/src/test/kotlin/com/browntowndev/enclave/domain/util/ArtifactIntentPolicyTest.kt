package com.browntowndev.enclave.domain.util

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ArtifactIntentPolicyTest {
    @Test
    fun `IGTA-01 ArtifactIntentPolicy matches write PDF prompt`() {
        assertTrue(
            ArtifactIntentPolicy.needsArtifactTools(
                "Please write a PDF report on quarterly revenue",
            ),
        )
    }

    @Test
    fun `pure web Q&A does not need artifact tools`() {
        assertFalse(
            ArtifactIntentPolicy.needsArtifactTools(
                "What are some future proof sectors for software engineers in 2025?",
            ),
        )
    }
}
