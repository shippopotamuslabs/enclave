package com.browntowndev.enclave.domain.util

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SearchIntentPolicyTest {
    @Test
    fun `heuristic blocks artifact and document creation prompts`() {
        val prompts =
            listOf(
                "Create me a document that outlines how making my own Android app and desktop app " +
                    "that has an agentic loop, tool calls, RAG, voice to text, BYOK, and other wrappers " +
                    "around the models can get me experience as an ML Ops engineer.",
                "I want this as an artifact that I can export",
                "No I mean use the document creation tool.",
                "Generate a PDF report from our chat",
                "Use the artifact tool please",
            )
        prompts.forEach { prompt ->
            assertTrue(
                SearchIntentPolicy.isHeuristicNonSearchPrompt(prompt),
                "Expected non-search heuristic for: $prompt",
            )
        }
    }

    @Test
    fun `IGTA-05b hasConcurrentResearchIntent true on combined prompt`() {
        assertTrue(
            SearchIntentPolicy.hasConcurrentResearchIntent(
                "Research current mortgage rates and write a PDF report",
            ),
        )
    }

    @Test
    fun `heuristic allows typical web lookup prompts`() {
        val prompts =
            listOf(
                "What are some future proof sectors for software engineers in 2025?",
                "What is the current state of AI regulation in Europe?",
                "What happened at Google IO this year?",
                "What companies in Columbus Ohio are hiring for cybersecurity roles right now?",
            )
        prompts.forEach { prompt ->
            assertFalse(
                SearchIntentPolicy.isHeuristicNonSearchPrompt(prompt),
                "Expected search-eligible prompt: $prompt",
            )
        }
    }
}
