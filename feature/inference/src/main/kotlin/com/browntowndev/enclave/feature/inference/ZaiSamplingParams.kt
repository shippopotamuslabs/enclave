package com.browntowndev.enclave.feature.inference

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Z.ai's OpenAI-compatible API rejects sampling parameters with more than two decimal places.
 */
internal object ZaiSamplingParams {
    private const val DECIMAL_SCALE = 2

    fun toApiDouble(value: Float): Double =
        BigDecimal(value.toString())
            .setScale(DECIMAL_SCALE, RoundingMode.HALF_UP)
            .toDouble()
}
