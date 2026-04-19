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

    // ==================== sanitizeOrQuery tests ====================

    @Test
    fun `sanitizeOrQuery with single query returns sanitized query`() {
        assertEquals("hello* world*", FtsSanitizer.sanitizeOrQuery(listOf("hello world")))
    }

    @Test
    fun `sanitizeOrQuery with multiple queries joins with OR`() {
        assertEquals("cow* OR cow* photo* OR moo*", FtsSanitizer.sanitizeOrQuery(listOf("cow", "cow photo", "moo")))
    }

    @Test
    fun `sanitizeOrQuery with empty list returns empty`() {
        assertEquals("", FtsSanitizer.sanitizeOrQuery(emptyList()))
    }

    @Test
    fun `sanitizeOrQuery filters out blank queries`() {
        assertEquals("hello*", FtsSanitizer.sanitizeOrQuery(listOf("hello", "   ", "")))
    }

    @Test
    fun `sanitizeOrQuery with all blank queries returns empty`() {
        assertEquals("", FtsSanitizer.sanitizeOrQuery(listOf("   ", "", "  ")))
    }

    @Test
    fun `sanitizeOrQuery strips FTS keywords from individual queries before joining`() {
        // "hello AND" should become "hello*" (AND stripped), "world OR NOT" becomes "world*" (OR, NOT stripped)
        assertEquals("hello* OR world*", FtsSanitizer.sanitizeOrQuery(listOf("hello AND", "world OR NOT")))
    }

    @Test
    fun `sanitizeOrQuery sanitizes special characters in each query`() {
        assertEquals("hello* OR world*", FtsSanitizer.sanitizeOrQuery(listOf("\"hello\"", "world!")))
    }

    @Test
    fun `sanitizeOrQuery supports Unicode in each query`() {
        assertEquals("こんにちは* OR привет*", FtsSanitizer.sanitizeOrQuery(listOf("こんにちは", "привет")))
    }

    @Test
    fun `sanitizeOrQuery with many queries produces correct OR chain`() {
        val queries = listOf("cow", "cows", "cattle", "moo", "mooing", "bovine")
        val expected = "cow* OR cows* OR cattle* OR moo* OR mooing* OR bovine*"
        assertEquals(expected, FtsSanitizer.sanitizeOrQuery(queries))
    }
}
