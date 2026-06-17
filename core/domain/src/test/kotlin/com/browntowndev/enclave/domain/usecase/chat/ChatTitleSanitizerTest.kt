package com.browntowndev.enclave.domain.usecase.chat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ChatTitleSanitizerTest {
    @Test
    @DisplayName("accepts a normal generated title")
    fun acceptsNormalTitle() {
        assertEquals("Weekend trip plan", ChatTitleSanitizer.sanitize("Weekend trip plan"))
    }

    @Test
    @DisplayName("strips special tokens and surrounding quotes")
    fun stripsSpecialTokensAndQuotes() {
        assertEquals("Budget travel tips", ChatTitleSanitizer.sanitize("<pad> \"Budget travel tips\" </s>"))
    }

    @Test
    @DisplayName("normalizes HTML entity fragments from tokenizer output")
    fun normalizesHtmlEntityFragments() {
        assertEquals("Slams", ChatTitleSanitizer.sanitize("#39;Slams #39;"))
    }

    @Test
    @DisplayName("rejects path-like degenerate model echo")
    fun rejectsPathLikeEcho() {
        assertNull(ChatTitleSanitizer.sanitize("er/State/State/State/State/State/State/State/"))
        assertNull(ChatTitleSanitizer.sanitize("s/Standard/Standard/Standard/Standard/S"))
        assertNull(ChatTitleSanitizer.sanitize("/State/State/State/State/State/State/State/S"))
    }

    @Test
    @DisplayName("rejects model update artifact suffixes")
    fun rejectsUpdateArtifactSuffixes() {
        assertNull(ChatTitleSanitizer.sanitize("Clickfunnels (Update 2)"))
        assertEquals("Clickfunnels landing page", ChatTitleSanitizer.sanitize("Clickfunnels landing page"))
    }

    @Test
    @DisplayName("rejects blank and null output")
    fun rejectsBlankOutput() {
        assertNull(ChatTitleSanitizer.sanitize(null))
        assertNull(ChatTitleSanitizer.sanitize("   "))
        assertNull(ChatTitleSanitizer.sanitize("<pad></s>"))
    }

    @Test
    @DisplayName("truncates long acceptable titles")
    fun truncatesLongTitles() {
        val longTitle = "A".repeat(70)
        assertEquals("A".repeat(60) + "…", ChatTitleSanitizer.sanitize(longTitle))
    }

    @Test
    @DisplayName("strips wrapping double-quotes produced by LFM2 decoder")
    fun stripsWrappingDoubleQuotes() {
        assertEquals("Refinancing Made Easy", ChatTitleSanitizer.sanitize("\"Refinancing Made Easy\""))
        assertEquals("Baking Sourdough Bread", ChatTitleSanitizer.sanitize("“Baking Sourdough Bread”"))
    }

    @Test
    @DisplayName("strips trailing period produced by LFM2 decoder")
    fun stripsTrailingPeriod() {
        assertEquals("Sourdough Bread Baking", ChatTitleSanitizer.sanitize("Sourdough Bread Baking."))
        assertEquals("How to Fix 500 Errors", ChatTitleSanitizer.sanitize("How to Fix 500 Errors."))
    }
}