package com.browntowndev.enclave.domain.profile

import com.browntowndev.enclave.domain.model.memory.Memory
import com.browntowndev.enclave.domain.model.memory.MemoryCategory
import com.browntowndev.enclave.domain.model.memory.MemoryRetrievalPolicy
import com.browntowndev.enclave.domain.usecase.chat.SearchToolPromptComposer
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * End-to-end verification that a PSYCH_PROFILE memory:
 * 1. Can be persisted (MemoryCategory has PSYCH_PROFILE)
 * 2. Is excluded from semantic retrieval
 * 3. Appears in the system prompt via getCoreMemories() → compose()
 */
class PsychProfileEndToEndTest {
    private val composer = SearchToolPromptComposer()

    @Test
    fun `PP-140 PSYCH_PROFILE memory round-trips through compose into PSYCHOLOGICAL PROFILE section`() {
        // Simulate: orchestrator saves profile, getCoreMemories() returns it, chat preparer passes it to compose()
        val profileText =
            """
            **Identity & Context:** Software engineer, mid-career transition, wrestling with work-life balance.
            **Communication Calibration:** Prefers direct feedback, dislikes hedging.
            **Psychological Patterns:** Avoidance tendency when discussing career uncertainty.
            """.trimIndent()

        val coreMemories =
            listOf(
                Memory(category = MemoryCategory.CORE_IDENTITY, content = "Software engineer, 10 years experience."),
                Memory(category = MemoryCategory.PSYCH_PROFILE, content = profileText),
            )

        val composed =
            composer.compose(
                baseSystemPrompt = "Be helpful.",
                toolCatalog = emptyList(),
                activeTools = emptyList(),
                coreMemories = coreMemories,
            )

        assertTrue(composed.contains("# PSYCHOLOGICAL PROFILE:"), "Missing PSYCHOLOGICAL PROFILE section")
        assertTrue(composed.contains("Avoidance tendency when discussing career uncertainty"))
        assertTrue(composed.contains("# ABOUT THE USER:"), "Missing ABOUT THE USER section")
        assertTrue(composed.contains("Software engineer, 10 years experience."))
    }

    @Test
    fun `PP-141 PSYCH_PROFILE section appears after ABOUT THE USER section`() {
        val coreMemories =
            listOf(
                Memory(category = MemoryCategory.CORE_IDENTITY, content = "Developer."),
                Memory(category = MemoryCategory.PSYCH_PROFILE, content = "Tends toward perfectionism."),
            )

        val composed =
            composer.compose(
                baseSystemPrompt = null,
                toolCatalog = emptyList(),
                activeTools = emptyList(),
                coreMemories = coreMemories,
            )

        val aboutIdx = composed.indexOf("# ABOUT THE USER:")
        val profileIdx = composed.indexOf("# PSYCHOLOGICAL PROFILE:")
        assertTrue(aboutIdx >= 0 && profileIdx > aboutIdx)
    }

    @Test
    fun `PP-142 PSYCH_PROFILE is excluded from semantic retrieval so it never appears twice`() {
        assertTrue(
            MemoryCategory.PSYCH_PROFILE in MemoryRetrievalPolicy.CHAT_EXCLUDED_CATEGORIES,
            "PSYCH_PROFILE must be in CHAT_EXCLUDED_CATEGORIES to avoid double-injection",
        )
    }

    @Test
    fun `PP-143 editing profile content changes what appears in subsequent compose calls`() {
        val originalContent = "User avoids vulnerability."
        val editedContent = "User avoids vulnerability. Growth trajectory: active therapy work."

        val withOriginal =
            composer.compose(
                baseSystemPrompt = null,
                toolCatalog = emptyList(),
                activeTools = emptyList(),
                coreMemories = listOf(Memory(category = MemoryCategory.PSYCH_PROFILE, content = originalContent)),
            )
        assertFalse(withOriginal.contains("active therapy work"))

        val withEdited =
            composer.compose(
                baseSystemPrompt = null,
                toolCatalog = emptyList(),
                activeTools = emptyList(),
                coreMemories = listOf(Memory(category = MemoryCategory.PSYCH_PROFILE, content = editedContent)),
            )
        assertTrue(withEdited.contains("active therapy work"))
    }

    @Test
    fun `PP-144 no PSYCH_PROFILE memory means no PSYCHOLOGICAL PROFILE section in system prompt`() {
        val composed =
            composer.compose(
                baseSystemPrompt = "Be helpful.",
                toolCatalog = emptyList(),
                activeTools = emptyList(),
                coreMemories = listOf(Memory(category = MemoryCategory.CORE_IDENTITY, content = "Developer.")),
            )
        assertFalse(composed.contains("# PSYCHOLOGICAL PROFILE:"))
    }

    @Test
    fun `PP-145 PSYCH_PROFILE memory category exists and can be created`() {
        val m = Memory(category = MemoryCategory.PSYCH_PROFILE, content = "Test profile.")
        assertTrue(m.category == MemoryCategory.PSYCH_PROFILE)
        assertTrue(m.content == "Test profile.")
    }
}
