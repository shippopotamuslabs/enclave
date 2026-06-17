package com.browntowndev.enclave.domain.usecase.inference

import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.inference.PromptModerationPort
import com.browntowndev.enclave.domain.port.inference.PromptModerationScores
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ModeratePromptUseCaseTest {
    @Test
    fun invoke_blankText_allowsWithoutClassifying() =
        runTest {
            val port = FakePromptModerationPort(PromptModerationScores(scoresByLabel = mapOf("nsfw" to 1.0)))
            val result = ModeratePromptUseCase(port, NoOpLoggingPort)("")

            assertFalse(result.blocked)
            assertEquals(0, port.classifyCount)
        }

    @Test
    fun invoke_nsfwAboveThreshold_blocks() =
        runTest {
            val useCase =
                ModeratePromptUseCase(
                    FakePromptModerationPort(
                        PromptModerationScores(
                            scoresByLabel = mapOf("nsfw" to 0.85, "safe" to 0.15),
                            thresholds = mapOf("nsfw" to 0.7),
                        ),
                    ),
                    NoOpLoggingPort,
                )

            val result = useCase("explicit content")

            assertTrue(result.blocked)
            assertEquals(listOf("nsfw"), result.triggeredLabels)
        }

    @Test
    fun invoke_nsfwAtThreshold_notBlocked() =
        runTest {
            val useCase =
                ModeratePromptUseCase(
                    FakePromptModerationPort(
                        PromptModerationScores(
                            scoresByLabel = mapOf("nsfw" to 0.70, "safe" to 0.30),
                            thresholds = mapOf("nsfw" to 0.7),
                        ),
                    ),
                    NoOpLoggingPort,
                )

            val result = useCase("borderline content")

            assertFalse(result.blocked)
        }

    @Test
    fun invoke_safeContent_notBlocked() =
        runTest {
            val useCase =
                ModeratePromptUseCase(
                    FakePromptModerationPort(
                        PromptModerationScores(
                            scoresByLabel = mapOf("nsfw" to 0.05, "safe" to 0.95),
                            thresholds = mapOf("nsfw" to 0.7),
                        ),
                    ),
                    NoOpLoggingPort,
                )

            val result = useCase("hello world")

            assertFalse(result.blocked)
        }

    @Test
    fun invoke_labelAlias_triggersOnCanonicalLabel() =
        runTest {
            val useCase =
                ModeratePromptUseCase(
                    FakePromptModerationPort(
                        PromptModerationScores(
                            scoresByLabel = mapOf("S3" to 0.80, "OK" to 0.20),
                            labelAliases = mapOf("nsfw" to listOf("S3")),
                            thresholds = mapOf("nsfw" to 0.7),
                        ),
                    ),
                    NoOpLoggingPort,
                )

            val result = useCase("explicit content")

            assertTrue(result.blocked)
            assertEquals(listOf("nsfw"), result.triggeredLabels)
        }

    @Test
    fun invoke_defaultThresholdUsed_whenThresholdsEmpty() =
        runTest {
            val useCase =
                ModeratePromptUseCase(
                    FakePromptModerationPort(
                        PromptModerationScores(
                            scoresByLabel = mapOf("nsfw" to 0.85),
                            labelAliases = mapOf("nsfw" to listOf("nsfw")),
                        ),
                    ),
                    NoOpLoggingPort,
                )

            val result = useCase("explicit content")

            assertTrue(result.blocked)
        }

    @Test
    fun invoke_returnsScoresByLabel() =
        runTest {
            val scores = mapOf("nsfw" to 0.85, "safe" to 0.15)
            val useCase =
                ModeratePromptUseCase(
                    FakePromptModerationPort(
                        PromptModerationScores(
                            scoresByLabel = scores,
                            thresholds = mapOf("nsfw" to 0.7),
                        ),
                    ),
                    NoOpLoggingPort,
                )

            val result = useCase("content")

            assertEquals(scores, result.scoresByLabel)
        }

    @Test
    fun invoke_blockedResult_hasNonZeroCombinedScore() =
        runTest {
            val useCase =
                ModeratePromptUseCase(
                    FakePromptModerationPort(
                        PromptModerationScores(
                            scoresByLabel = mapOf("nsfw" to 0.90),
                            thresholds = mapOf("nsfw" to 0.7),
                        ),
                    ),
                    NoOpLoggingPort,
                )

            val result = useCase("content")

            assertTrue(result.blocked)
            assertEquals(0.90, result.combinedIllegalScore, 0.001)
        }

    private object NoOpLoggingPort : LoggingPort {
        override fun debug(tag: String, message: String) = Unit
        override fun info(tag: String, message: String) = Unit
        override fun warning(tag: String, message: String) = Unit
        override fun error(tag: String, message: String, throwable: Throwable?) = Unit
    }

    private class FakePromptModerationPort(
        private val scores: PromptModerationScores,
    ) : PromptModerationPort {
        var classifyCount = 0
            private set

        override suspend fun classify(prompt: String): PromptModerationScores {
            classifyCount += 1
            return scores
        }
    }
}
