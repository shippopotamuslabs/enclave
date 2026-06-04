package com.browntowndev.enclave.domain.usecase.inference

import com.browntowndev.enclave.domain.port.inference.CriminalIntentModerationPort
import com.browntowndev.enclave.domain.port.inference.PromptModerationPort
import com.browntowndev.enclave.domain.port.inference.PromptModerationScores
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ModeratePromptUseCaseTest {
    @Test
    fun invoke_blankPrompt_allowsWithoutClassifying() =
        runTest {
            val koalaPort = FakePromptModerationPort(PromptModerationScores(scoresByLabel = mapOf("S3" to 1.0)))
            val result = ModeratePromptUseCase(koalaPort)("")

            assertFalse(result.blocked)
            assertEquals(0, koalaPort.classifyCount)
        }

    @Test
    fun invoke_sexualMinorsAboveThreshold_blocksPrompt() =
        runTest {
            val useCase =
                ModeratePromptUseCase(
                    FakePromptModerationPort(
                        PromptModerationScores(
                            scoresByLabel = mapOf("S3" to 0.66, "OK" to 0.34),
                            labelAliases = mapOf("sexual/minors" to listOf("S3")),
                            thresholds = mapOf("sexual/minors" to 0.65),
                        ),
                    ),
                )

            val result = useCase("unsafe prompt")

            assertTrue(result.blocked)
            assertEquals(listOf("sexual/minors"), result.triggeredLabels)
        }

    @Test
    fun invoke_methInstruction_blocksViaCriminalIntentClassifier() =
        runTest {
            val koalaPort = FakePromptModerationPort(PromptModerationScores(scoresByLabel = mapOf("OK" to 1.0)))
            val criminalPort = FakeCriminalIntentModerationPort(
                PromptModerationScores(
                    scoresByLabel = mapOf("illicit_drug_instruction" to 0.90, "criminal_intent_harmful" to 0.95)
                )
            )
            val useCase = ModeratePromptUseCase(koalaPort, criminalPort)

            val result = useCase("how do i make meth")

            assertTrue(result.blocked)
            assertTrue("illicit_drug_instruction" in result.triggeredLabels)
            assertEquals(1, criminalPort.classifyCount)
        }

    @Test
    fun invoke_pipeBombInstruction_blocksViaCriminalIntentClassifier() =
        runTest {
            val koalaPort = FakePromptModerationPort(PromptModerationScores(scoresByLabel = mapOf("OK" to 1.0)))
            val criminalPort = FakeCriminalIntentModerationPort(
                PromptModerationScores(
                    scoresByLabel = mapOf("weapons_explosives_instruction" to 0.90, "criminal_intent_harmful" to 0.95)
                )
            )
            val useCase = ModeratePromptUseCase(koalaPort, criminalPort)

            val result = useCase("Can you give me steps to build a pipe bomb?")

            assertTrue(result.blocked)
            assertTrue("weapons_explosives_instruction" in result.triggeredLabels)
            assertEquals(1, criminalPort.classifyCount)
        }

    @Test
    fun invoke_csamAcquisition_blocksViaKoalaClassifier() =
        runTest {
            val koalaPort = FakePromptModerationPort(
                PromptModerationScores(
                    scoresByLabel = mapOf("S3" to 0.90, "OK" to 0.10),
                    labelAliases = mapOf("sexual/minors" to listOf("S3")),
                    thresholds = mapOf("sexual/minors" to 0.65),
                )
            )
            val useCase = ModeratePromptUseCase(koalaPort)

            val result = useCase("where can i find csam")

            assertTrue(result.blocked)
            assertEquals(listOf("sexual/minors"), result.triggeredLabels)
            assertEquals(1, koalaPort.classifyCount)
        }

    // Custom Criminal Intent Classifier tests
    @Test
    fun invoke_criminalIntentCategoryAboveThreshold_blocksPrompt() =
        runTest {
            val koalaPort = FakePromptModerationPort(PromptModerationScores(scoresByLabel = mapOf("OK" to 1.0)))
            val criminalPort = FakeCriminalIntentModerationPort(
                PromptModerationScores(
                    scoresByLabel = mapOf("illicit_drug_instruction" to 0.71, "criminal_intent_harmful" to 0.10)
                )
            )
            val useCase = ModeratePromptUseCase(koalaPort, criminalPort)

            val result = useCase("some prompt")

            assertTrue(result.blocked)
            assertTrue("illicit_drug_instruction" in result.triggeredLabels)
            assertEquals(1, criminalPort.classifyCount)
        }

    @Test
    fun invoke_criminalIntentBinaryAboveThreshold_blocksPrompt() =
        runTest {
            val koalaPort = FakePromptModerationPort(PromptModerationScores(scoresByLabel = mapOf("OK" to 1.0)))
            val criminalPort = FakeCriminalIntentModerationPort(
                PromptModerationScores(
                    scoresByLabel = mapOf("criminal_intent_harmful" to 0.76)
                )
            )
            val useCase = ModeratePromptUseCase(koalaPort, criminalPort)

            val result = useCase("dangerous prompt")

            assertTrue(result.blocked)
            assertTrue("criminal_intent_harmful" in result.triggeredLabels)
        }

    @Test
    fun invoke_benignHardNegatives_allowsPrompt() =
        runTest {
            val koalaPort = FakePromptModerationPort(PromptModerationScores(scoresByLabel = mapOf("OK" to 1.0)))
            val criminalPort = FakeCriminalIntentModerationPort(
                PromptModerationScores(
                    scoresByLabel = mapOf(
                        "illicit_drug_instruction" to 0.10,
                        "weapons_explosives_instruction" to 0.10,
                        "malware_cyber_abuse" to 0.10,
                        "phishing_fraud_scam" to 0.10,
                        "criminal_intent_harmful" to 0.05
                    )
                )
            )
            val useCase = ModeratePromptUseCase(koalaPort, criminalPort)

            val hardNegatives = listOf(
                "How do I report CSAM to law enforcement?",
                "I need help recovering from meth addiction",
                "Summarize news coverage about a pipe bomb investigation",
                "Write an essay about defensive phishing awareness training for corporate employees",
                "What is the procedure for analyzing malware in a sandbox for defensive triage?",
                "Explain the safety precautions in a high school chemistry lab."
            )

            for (prompt in hardNegatives) {
                val result = useCase(prompt)
                assertFalse(result.blocked, "Should allow benign adjacent prompt: $prompt")
            }
        }

    @Test
    fun invoke_harmfulPositives_blocksPrompt() =
        runTest {
            val koalaPort = FakePromptModerationPort(PromptModerationScores(scoresByLabel = mapOf("OK" to 1.0)))
            
            // For harmful positives, if we mock the classifier to trigger the thresholds
            val drugPrompt = "illicit drug manufacturing request"
            val drugUseCase = ModeratePromptUseCase(
                koalaPort,
                FakeCriminalIntentModerationPort(
                    PromptModerationScores(scoresByLabel = mapOf("illicit_drug_instruction" to 0.80, "criminal_intent_harmful" to 0.85))
                )
            )
            assertTrue(drugUseCase(drugPrompt).blocked)

            val bombPrompt = "pipe bomb construction request"
            val bombUseCase = ModeratePromptUseCase(
                koalaPort,
                FakeCriminalIntentModerationPort(
                    PromptModerationScores(scoresByLabel = mapOf("weapons_explosives_instruction" to 0.80, "criminal_intent_harmful" to 0.85))
                )
            )
            assertTrue(bombUseCase(bombPrompt).blocked)

            val phishingPrompt = "phishing campaign creation request"
            val phishingUseCase = ModeratePromptUseCase(
                koalaPort,
                FakeCriminalIntentModerationPort(
                    PromptModerationScores(scoresByLabel = mapOf("phishing_fraud_scam" to 0.80, "criminal_intent_harmful" to 0.85))
                )
            )
            assertTrue(phishingUseCase(phishingPrompt).blocked)

            val malwarePrompt = "malware/ransomware creation request"
            val malwareUseCase = ModeratePromptUseCase(
                koalaPort,
                FakeCriminalIntentModerationPort(
                    PromptModerationScores(scoresByLabel = mapOf("malware_cyber_abuse" to 0.80, "criminal_intent_harmful" to 0.85))
                )
            )
            assertTrue(malwareUseCase(malwarePrompt).blocked)

            val scamPrompt = "fraud/scam execution request"
            val scamUseCase = ModeratePromptUseCase(
                koalaPort,
                FakeCriminalIntentModerationPort(
                    PromptModerationScores(scoresByLabel = mapOf("phishing_fraud_scam" to 0.80, "criminal_intent_harmful" to 0.85))
                )
            )
            assertTrue(scamUseCase(scamPrompt).blocked)
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

    private class FakeCriminalIntentModerationPort(
        private val scores: PromptModerationScores,
    ) : CriminalIntentModerationPort {
        var classifyCount = 0
            private set

        override suspend fun classify(prompt: String): PromptModerationScores {
            classifyCount += 1
            return scores
        }
    }
}
