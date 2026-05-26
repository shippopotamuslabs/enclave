package com.browntowndev.enclave.domain.model.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ApiModelPricingFormatTest {
    @Test
    fun `formatUsdPerMillion formats normalized prices without scientific notation`() {
        assertEquals("2", ApiModelPricingFormat.formatUsdPerMillion(2.0))
        assertEquals("0.2", ApiModelPricingFormat.formatUsdPerMillion(0.2))
        assertEquals("1,234.5678", ApiModelPricingFormat.formatUsdPerMillion(1234.5678))
    }

    @Test
    fun `formatUsdPerMillion hides invalid values`() {
        assertNull(ApiModelPricingFormat.formatUsdPerMillion(-1.0))
        assertNull(ApiModelPricingFormat.formatUsdPerMillion(Double.NaN))
        assertNull(ApiModelPricingFormat.formatUsdPerMillion(Double.POSITIVE_INFINITY))
    }

    @Test
    fun `parseUsdPerMillion accepts formatted input`() {
        assertEquals(1.25, ApiModelPricingFormat.parseUsdPerMillion("1.25"))
        assertEquals(1234.5678, ApiModelPricingFormat.parseUsdPerMillion("1,234.5678"))
        assertNull(ApiModelPricingFormat.parseUsdPerMillion(""))
        assertNull(ApiModelPricingFormat.parseUsdPerMillion("not-a-number"))
    }

    @Test
    fun `displayUsdPerMillion shows em dash when missing`() {
        assertEquals("—", ApiModelPricingFormat.displayUsdPerMillion(null))
        assertEquals("$0.2 / 1M tokens", ApiModelPricingFormat.displayUsdPerMillion(0.2))
    }
}
