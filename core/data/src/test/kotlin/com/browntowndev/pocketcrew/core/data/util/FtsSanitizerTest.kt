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

    @Test
    fun `sanitize preserves underscores and alphanumeric characters`() {
        assertEquals("user_123* chat_id*", FtsSanitizer.sanitize("user_123 chat_id"))
    }

    @Test
    fun `sanitize preserves accented characters`() {
        // e + combining acute accent
        val accentedE = "e\u0301" 
        assertEquals("${accentedE}*", FtsSanitizer.sanitize(accentedE))
        // single code point é
        assertEquals("é*", FtsSanitizer.sanitize("é"))
    }
}
