package com.browntowndev.enclave.feature.inference.moderation

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal data class ModerationAssetConfig(
    val modelPath: String,
    val tokenizerPath: String,
    val maxSequenceLength: Int,
    val labels: List<String>,
    val labelAliases: Map<String, List<String>>,
    val thresholds: Map<String, Double>,
    val combinedIllegalLabels: List<String>,
) {
    companion object {
        fun parse(configJson: String): ModerationAssetConfig {
            val root = Json.parseToJsonElement(configJson).jsonObject
            return ModerationAssetConfig(
                modelPath = root.getValue("model_path").jsonPrimitive.content,
                tokenizerPath = root.getValue("tokenizer_path").jsonPrimitive.content,
                maxSequenceLength = root["max_sequence_length"]?.jsonPrimitive?.intOrNull ?: DEFAULT_MAX_SEQUENCE_LENGTH,
                labels =
                    root
                        .getValue("labels")
                        .jsonArray
                        .map { label -> label.jsonPrimitive.content },
                labelAliases =
                    root["label_aliases"]
                        ?.jsonObject
                        ?.mapValues { (_, aliases) ->
                            aliases.jsonArray.map { alias -> alias.jsonPrimitive.content }
                        } ?: emptyMap(),
                thresholds =
                    root["thresholds"]
                        ?.jsonObject
                        ?.mapValues { (_, threshold) -> threshold.jsonPrimitive.double } ?: emptyMap(),
                combinedIllegalLabels =
                    root["combined_illegal_labels"]
                        ?.jsonArray
                        ?.map { label -> label.jsonPrimitive.content } ?: emptyList(),
            )
        }

        private const val DEFAULT_MAX_SEQUENCE_LENGTH = 384
    }
}
