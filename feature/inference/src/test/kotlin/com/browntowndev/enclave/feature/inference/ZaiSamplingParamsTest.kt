package com.browntowndev.enclave.feature.inference

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ZaiSamplingParamsTest {
    @Test
    fun `toApiDouble preserves two-decimal preset values`() {
        assertEquals(0.95, ZaiSamplingParams.toApiDouble(0.95f))
        assertEquals(0.7, ZaiSamplingParams.toApiDouble(0.7f))
        assertEquals(1.0, ZaiSamplingParams.toApiDouble(1.0f))
    }

    @Test
    fun `toApiDouble rounds float binary artifacts to two decimals`() {
        assertEquals(0.95, ZaiSamplingParams.toApiDouble(0.949999988079071f))
        assertEquals(0.9, ZaiSamplingParams.toApiDouble(0.8999999761581421f))
    }

    @Test
    fun `toApiDouble rounds half-up at the third decimal`() {
        assertEquals(0.96, ZaiSamplingParams.toApiDouble(0.955f))
        assertEquals(0.13, ZaiSamplingParams.toApiDouble(0.125f))
    }
}