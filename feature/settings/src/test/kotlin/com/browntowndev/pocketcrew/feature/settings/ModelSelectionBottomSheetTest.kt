package com.browntowndev.pocketcrew.feature.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ModelSelectionBottomSheetTest {

    @Test
    fun `formatUsdPerMillion formats normalized prices without scientific notation`() {
        assertEquals("2", 2.0.formatUsdPerMillion())
        assertEquals("0.2", 0.2.formatUsdPerMillion())
        assertEquals("1,234.5678", 1234.5678.formatUsdPerMillion())
    }

    @Test
    fun `formatUsdPerMillion hides invalid values`() {
        assertNull((-1.0).formatUsdPerMillion())
        assertNull(Double.NaN.formatUsdPerMillion())
        assertNull(Double.POSITIVE_INFINITY.formatUsdPerMillion())
    }
}
