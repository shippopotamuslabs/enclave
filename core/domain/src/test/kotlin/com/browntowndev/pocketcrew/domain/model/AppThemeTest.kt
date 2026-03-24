package com.browntowndev.pocketcrew.domain.model.settings

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class AppThemeTest {

    @Test
    fun `AppTheme has correct values`() {
        val themes = AppTheme.entries
        assertEquals(4, themes.size)
        assertTrue(themes.contains(AppTheme.SYSTEM))
        assertTrue(themes.contains(AppTheme.DYNAMIC))
        assertTrue(themes.contains(AppTheme.DARK))
        assertTrue(themes.contains(AppTheme.LIGHT))
    }

    @Test
    fun `AppTheme valueOf returns correct enum`() {
        assertEquals(AppTheme.SYSTEM, AppTheme.valueOf("SYSTEM"))
        assertEquals(AppTheme.DYNAMIC, AppTheme.valueOf("DYNAMIC"))
        assertEquals(AppTheme.DARK, AppTheme.valueOf("DARK"))
        assertEquals(AppTheme.LIGHT, AppTheme.valueOf("LIGHT"))
    }

    @Test
    fun `AppTheme valueOf throws for invalid name`() {
        assertThrows(IllegalArgumentException::class.java) {
            AppTheme.valueOf("INVALID")
        }
    }
}

