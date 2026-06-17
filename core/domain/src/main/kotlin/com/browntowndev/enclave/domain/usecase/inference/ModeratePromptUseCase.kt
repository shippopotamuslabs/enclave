package com.browntowndev.enclave.domain.usecase.inference

import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.inference.PromptModerationPort
import javax.inject.Inject

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
        private val loggingPort: LoggingPort,
    ) {
        suspend operator fun invoke(text: String): PromptModerationResult {
            if (text.isBlank()) {
                return PromptModerationResult(
                    blocked = false,
                    scoresByLabel = emptyMap(),
                    canonicalScores = emptyMap(),
                    triggeredLabels = emptyList(),
                    combinedIllegalScore = 0.0,
                )
            }

            val scores = moderationPort.classify(text)
            val thresholds = DEFAULT_THRESHOLDS + scores.thresholds
            val canonicalScores =
                scores.labelAliases.mapValues { (_, labels) ->
                    labels.maxOfOrNull { label -> scores.scoresByLabel[label] ?: 0.0 } ?: 0.0
                }

            val triggeredLabels =
                (canonicalScores.keys + thresholds.keys).filter { label ->
                    val score = canonicalScores[label] ?: scores.scoresByLabel[label] ?: 0.0
                    val threshold = thresholds[label] ?: DEFAULT_THRESHOLD
                    score > threshold
                }

            val combinedIllegalScore =
                triggeredLabels.maxOfOrNull { label ->
                    canonicalScores[label] ?: scores.scoresByLabel[label] ?: 0.0
                } ?: 0.0

            loggingPort.debug(
                TAG,
                "Moderation scores: raw=${scores.scoresByLabel}, " +
                    "canonical=$canonicalScores, triggered=$triggeredLabels, " +
                    "combinedIllegal=$combinedIllegalScore, blocked=${triggeredLabels.isNotEmpty()}",
            )

            return PromptModerationResult(
                blocked = triggeredLabels.isNotEmpty(),
                scoresByLabel = scores.scoresByLabel,
                canonicalScores = canonicalScores,
                triggeredLabels = triggeredLabels,
                combinedIllegalScore = combinedIllegalScore,
            )
        }

        private companion object {
            private const val TAG = "ModeratePromptUseCase"
            private const val DEFAULT_THRESHOLD = 0.7
            private val DEFAULT_THRESHOLDS: Map<String, Double> = emptyMap()
        }
    }
