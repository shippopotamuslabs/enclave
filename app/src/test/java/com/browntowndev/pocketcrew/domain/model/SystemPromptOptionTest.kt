package com.browntowndev.pocketcrew.domain.model

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class SystemPromptOptionTest {

    @Test
    fun `SystemPromptOption has correct values`() {
        val options = SystemPromptOption.entries
        assertEquals(4, options.size)
    }

    @Test
    fun `SystemPromptOption CUSTOM has empty stubPrompt`() {
        assertEquals("Custom", SystemPromptOption.CUSTOM.displayName)
        assertEquals("", SystemPromptOption.CUSTOM.stubPrompt)
    }

    @Test
    fun `SystemPromptOption CONCISE has correct properties`() {
        assertEquals("Concise", SystemPromptOption.CONCISE.displayName)
        assertTrue(SystemPromptOption.CONCISE.stubPrompt.contains("concise", ignoreCase = true))
    }

    @Test
    fun `SystemPromptOption FORMAL has correct properties`() {
        assertEquals("Formal", SystemPromptOption.FORMAL.displayName)
        assertTrue(SystemPromptOption.FORMAL.stubPrompt.contains("formal", ignoreCase = true))
    }

    @Test
    fun `SystemPromptOption RIGOROUS has correct properties`() {
        assertEquals("Rigorous", SystemPromptOption.RIGOROUS.displayName)
        assertTrue(SystemPromptOption.RIGOROUS.stubPrompt.contains("rigorous", ignoreCase = true))
    }

    @Test
    fun `SystemPromptOption valueOf returns correct enum`() {
        assertEquals(SystemPromptOption.CUSTOM, SystemPromptOption.valueOf("CUSTOM"))
        assertEquals(SystemPromptOption.CONCISE, SystemPromptOption.valueOf("CONCISE"))
        assertEquals(SystemPromptOption.FORMAL, SystemPromptOption.valueOf("FORMAL"))
        assertEquals(SystemPromptOption.RIGOROUS, SystemPromptOption.valueOf("RIGOROUS"))
    }

    @Test
    fun `SystemPromptOption valueOf throws for invalid name`() {
        assertThrows(IllegalArgumentException::class.java) {
            SystemPromptOption.valueOf("INVALID")
        }
    }

    @Test
    fun `All SystemPromptOption have non-empty displayName`() {
        SystemPromptOption.entries.forEach { option ->
            assertTrue(option.displayName.isNotBlank(), "displayName should not be blank for $option")
        }
    }
}

