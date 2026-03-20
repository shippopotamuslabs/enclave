package com.browntowndev.pocketcrew.domain.usecase.chat

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse

class SafetyProbeImplTest {

    private lateinit var useCase: SafetyProbeImpl

    @BeforeEach
    fun setup() {
        useCase = SafetyProbeImpl()
    }

    @Test
    fun `isSafe returns true for normal content`() {
        // Given
        val normalContent = "Hello, how are you?"

        // When
        val result = useCase.isSafe(normalContent)

        // Then
        assertTrue(result)
    }

    @Test
    fun `isSafe returns false for blank content`() {
        // When/Then - blank content
        assertFalse(useCase.isSafe(""))

        // Whitespace only
        assertFalse(useCase.isSafe("   "))

        // Tab/newline only
        assertFalse(useCase.isSafe("\t\n"))
    }

    @Test
    fun `isSafe returns true for long content`() {
        // Given
        val longContent = "A".repeat(10000) // 10k characters

        // When
        val result = useCase.isSafe(longContent)

        // Then
        assertTrue(result)
    }

    @Test
    fun `isSafe handles unicode content`() {
        // Given
        val unicodeContent = "你好世界! 🌍 émoji"

        // When
        val result = useCase.isSafe(unicodeContent)

        // Then
        assertTrue(result)
    }

    @Test
    fun `isSafe returns true for content with special characters`() {
        // Given
        val specialContent = "Hello <script>alert('xss')</script> world"

        // When
        val result = useCase.isSafe(specialContent)

        // Then - current implementation only checks non-blank
        assertTrue(result)
    }

    @Test
    fun `isSafe returns true for single character`() {
        // Given
        val singleChar = "A"

        // When
        val result = useCase.isSafe(singleChar)

        // Then
        assertTrue(result)
    }

    @Test
    fun `isSafe returns true for numbers only`() {
        // Given
        val numbersOnly = "12345"

        // When
        val result = useCase.isSafe(numbersOnly)

        // Then
        assertTrue(result)
    }

    @Test
    fun `isSafe returns true for mixed content`() {
        // Given
        val mixedContent = "Hello, world! This is a test message with numbers 123 and symbols @#\$%"

        // When
        val result = useCase.isSafe(mixedContent)

        // Then
        assertTrue(result)
    }
}
