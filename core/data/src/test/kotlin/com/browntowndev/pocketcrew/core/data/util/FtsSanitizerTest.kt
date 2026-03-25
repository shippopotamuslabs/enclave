package com.browntowndev.pocketcrew.core.data.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FtsSanitizerTest {

    @Test
    fun `sanitize empty string returns empty`() {
        assertEquals("", FtsSanitizer.sanitize(""))
        assertEquals("", FtsSanitizer.sanitize("   "))
    }

    @Test
    fun `sanitize simple query adds wildcards to all terms`() {
        assertEquals("hello* world*", FtsSanitizer.sanitize("hello world"))
    }

    @Test
    fun `sanitize special characters removes them and keeps word terms`() {
        assertEquals("hello* world*", FtsSanitizer.sanitize("\"*^\" hello world"))
    }

    @Test
    fun `sanitize standalone FTS keywords filters them to prevent syntax errors`() {
        assertEquals("hello* world*", FtsSanitizer.sanitize("hello AND OR world NOT"))
    }

    @Test
    fun `sanitize supports Unicode characters for non-English queries`() {
        // Japanese: "hello" in Hiragana
        assertEquals("こんにちは*", FtsSanitizer.sanitize("こんにちは"))
        // Cyrillic
        assertEquals("привет*", FtsSanitizer.sanitize("привет"))
    }

    @Test
    fun `sanitize multiple spaces collapses into single spaces`() {
        assertEquals("hello* world*", FtsSanitizer.sanitize("hello    world"))
    }
}
