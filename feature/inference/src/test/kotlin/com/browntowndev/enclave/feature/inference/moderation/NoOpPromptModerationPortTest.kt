package com.browntowndev.enclave.feature.inference.moderation

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class NoOpPromptModerationPortTest {
    @Test
    @DisplayName("classify returns empty scores for any input")
    fun classifyReturnsEmptyScores() =
        runTest {
            val scores = NoOpPromptModerationPort.classify("some text")

            assertTrue(scores.scoresByLabel.isEmpty())
            assertTrue(scores.labelAliases.isEmpty())
            assertTrue(scores.thresholds.isEmpty())
        }

    @Test
    @DisplayName("classify returns empty scores for blank input")
    fun classifyReturnsEmptyScoresForBlankInput() =
        runTest {
            val scores = NoOpPromptModerationPort.classify("")

            assertTrue(scores.scoresByLabel.isEmpty())
        }

    @Test
    @DisplayName("classify returns empty scores for explicit content")
    fun classifyReturnsEmptyScoresForExplicitContent() =
        runTest {
            val scores = NoOpPromptModerationPort.classify("very explicit content")

            assertTrue(scores.scoresByLabel.isEmpty())
        }
}
