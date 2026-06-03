package com.browntowndev.enclave.domain.usecase.inference

import com.browntowndev.enclave.domain.port.inference.CriminalIntentModerationPort
import com.browntowndev.enclave.domain.port.inference.PromptModerationPort
import com.browntowndev.enclave.domain.port.inference.PromptModerationScores
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import kotlin.math.max

data class PromptModerationResult(
    val blocked: Boolean,
    val scoresByLabel: Map<String, Double>,
    val canonicalScores: Map<String, Double>,
    val triggeredLabels: List<String>,
    val combinedIllegalScore: Double,
)

class ModeratePromptUseCase
    @Inject
    constructor(
        private val moderationPort: PromptModerationPort,
        private val criminalIntentPort: CriminalIntentModerationPort = CriminalIntentModerationPort.Dummy,
    ) {
        suspend operator fun invoke(prompt: String): PromptModerationResult {
            if (prompt.isBlank()) {
                return PromptModerationResult(
                    blocked = false,
                    scoresByLabel = emptyMap(),
                    canonicalScores = emptyMap(),
                    triggeredLabels = emptyList(),
                    combinedIllegalScore = 0.0,
                )
            }

            return coroutineScope {
                val koalaDeferred = async { moderationPort.classify(prompt) }
                val criminalDeferred = async { criminalIntentPort.classify(prompt) }

                val koalaScores = koalaDeferred.await()
                val criminalScores = criminalDeferred.await()

                // 1. Koala (sexual/minors) processing
                val koalaThresholds = KOALA_DEFAULT_THRESHOLDS + koalaScores.thresholds
                val koalaCanonicalScores =
                    koalaScores.labelAliases.mapValues { (_, labels) ->
                        labels.maxOfOrNull { label -> koalaScores.scoresByLabel[label] ?: 0.0 } ?: 0.0
                    }
                val koalaSexualMinorsScore = koalaCanonicalScores["sexual/minors"]
                    ?: koalaScores.scoresByLabel["sexual/minors"]
                    ?: koalaScores.scoresByLabel["S3"] // fallback to raw label
                    ?: 0.0

                val koalaSexualMinorsThreshold = koalaThresholds["sexual/minors"] ?: DEFAULT_KOALA_SEXUAL_MINORS_THRESHOLD
                val koalaTriggered = koalaSexualMinorsScore > koalaSexualMinorsThreshold

                // 2. Custom criminal intent classifier processing
                val criminalThresholds = CRIMINAL_DEFAULT_THRESHOLDS + criminalScores.thresholds
                val criminalCanonicalScores =
                    criminalScores.labelAliases.mapValues { (_, labels) ->
                        labels.maxOfOrNull { label -> criminalScores.scoresByLabel[label] ?: 0.0 } ?: 0.0
                    }

                val triggeredCriminalLabels = mutableListOf<String>()
                CRIMINAL_DEFAULT_THRESHOLDS.forEach { (label, defaultThreshold) ->
                    val threshold = criminalThresholds[label] ?: defaultThreshold
                    val score = criminalCanonicalScores[label] ?: criminalScores.scoresByLabel[label] ?: 0.0
                    if (score > threshold) {
                        triggeredCriminalLabels.add(label)
                    }
                }

                val triggeredLabels = mutableListOf<String>()
                if (koalaTriggered) {
                    triggeredLabels.add("sexual/minors")
                }
                triggeredLabels.addAll(triggeredCriminalLabels)

                val combinedIllegalScore = criminalScores.scoresByLabel["criminal_intent_harmful"]
                    ?: max(
                        triggeredCriminalLabels.maxOfOrNull { label -> criminalScores.scoresByLabel[label] ?: 0.0 } ?: 0.0,
                        koalaSexualMinorsScore
                    )

                val blocked = triggeredLabels.isNotEmpty()

                val combinedScoresByLabel = koalaScores.scoresByLabel + criminalScores.scoresByLabel
                val combinedCanonicalScores = koalaCanonicalScores + criminalCanonicalScores

                PromptModerationResult(
                    blocked = blocked,
                    scoresByLabel = combinedScoresByLabel,
                    canonicalScores = combinedCanonicalScores,
                    triggeredLabels = triggeredLabels,
                    combinedIllegalScore = combinedIllegalScore,
                )
            }
        }

        private companion object {
            private const val DEFAULT_KOALA_SEXUAL_MINORS_THRESHOLD = 0.65

            private const val DEFAULT_ILLICIT_DRUG_THRESHOLD = 0.70
            private const val DEFAULT_WEAPONS_EXPLOSIVES_THRESHOLD = 0.70
            private const val DEFAULT_MALWARE_CYBER_THRESHOLD = 0.72
            private const val DEFAULT_PHISHING_FRAUD_THRESHOLD = 0.72
            private const val DEFAULT_VIOLENT_CRIME_THRESHOLD = 0.72
            private const val DEFAULT_OTHER_ILLEGAL_THRESHOLD = 0.78
            private const val DEFAULT_CRIMINAL_HARM_THRESHOLD = 0.75

            private val KOALA_DEFAULT_THRESHOLDS =
                mapOf(
                    "sexual/minors" to DEFAULT_KOALA_SEXUAL_MINORS_THRESHOLD,
                )

            private val CRIMINAL_DEFAULT_THRESHOLDS =
                mapOf(
                    "illicit_drug_instruction" to DEFAULT_ILLICIT_DRUG_THRESHOLD,
                    "weapons_explosives_instruction" to DEFAULT_WEAPONS_EXPLOSIVES_THRESHOLD,
                    "malware_cyber_abuse" to DEFAULT_MALWARE_CYBER_THRESHOLD,
                    "phishing_fraud_scam" to DEFAULT_PHISHING_FRAUD_THRESHOLD,
                    "violent_or_organized_crime" to DEFAULT_VIOLENT_CRIME_THRESHOLD,
                    "other_illegal_instruction" to DEFAULT_OTHER_ILLEGAL_THRESHOLD,
                    "criminal_intent_harmful" to DEFAULT_CRIMINAL_HARM_THRESHOLD,
                )
        }
    }
