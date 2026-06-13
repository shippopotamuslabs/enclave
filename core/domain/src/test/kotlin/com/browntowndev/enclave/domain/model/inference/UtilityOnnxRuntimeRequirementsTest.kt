package com.browntowndev.enclave.domain.model.inference

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UtilityOnnxRuntimeRequirementsTest {
    @Test
    fun requiredHeadroom_includesChatModelReserveWhenLoaded() {
        val loaded = UtilityOnnxRuntimeRequirements.requiredHeadroomBytes(chatModelLoaded = true)
        val idle = UtilityOnnxRuntimeRequirements.requiredHeadroomBytes(chatModelLoaded = false)
        assertEquals(
            UtilityOnnxRuntimeRequirements.LFM2_SESSION_BYTES +
                UtilityOnnxRuntimeRequirements.IN_FLIGHT_CHAT_MODEL_BYTES,
            loaded,
        )
        assertEquals(UtilityOnnxRuntimeRequirements.LFM2_SESSION_BYTES, idle)
    }
}
