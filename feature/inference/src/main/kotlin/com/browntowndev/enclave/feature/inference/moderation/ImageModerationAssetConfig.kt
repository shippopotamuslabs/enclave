package com.browntowndev.enclave.feature.inference.moderation

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.float
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Parsed `model_config.json` for an ONNX image-safety classifier.
 *
 * The config declares the model artifact path, the classifier's labels, the per-label score
 * thresholds, the subset of labels that trigger a block, the network's spatial input size, and
 * the channel mean/std used for normalization. Numeric normalization lists follow RGB order.
 */
internal data class ImageModerationAssetConfig(
    val modelPath: String,
    val inputSize: Int,
    val labels: List<String>,
    val thresholds: Map<String, Float>,
    val blockedLabels: List<String>,
    val channelMean: List<Float>,
    val channelStd: List<Float>,
    val inputName: String,
    val outputName: String,
    val resizeMode: ImageModerationResizeMode = ImageModerationResizeMode.CENTER_CROP,
) {
    companion object {
        fun parse(configJson: String): ImageModerationAssetConfig {
            val root = Json.parseToJsonElement(configJson).jsonObject
            return ImageModerationAssetConfig(
                modelPath = root.getValue("model_path").jsonPrimitive.content,
                inputSize = root["input_size"]?.jsonPrimitive?.intOrNull ?: DEFAULT_INPUT_SIZE,
                labels = root.getValue("labels").jsonArray.map { it.jsonPrimitive.content },
                thresholds = root.parseFloatMap("thresholds"),
                blockedLabels = root.getStringList("blocked_labels"),
                channelMean = root.parseFloatList("channel_mean", default = DEFAULT_IMAGENET_MEAN),
                channelStd = root.parseFloatList("channel_std", default = DEFAULT_IMAGENET_STD),
                inputName = root["input_name"]?.jsonPrimitive?.contentOrNull ?: DEFAULT_INPUT_NAME,
                outputName = root["output_name"]?.jsonPrimitive?.contentOrNull ?: DEFAULT_OUTPUT_NAME,
                resizeMode = ImageModerationResizeMode.fromConfigValue(root["resize_mode"]?.jsonPrimitive?.contentOrNull),
            )
        }

        private fun JsonObject.parseFloatMap(key: String): Map<String, Float> =
            this[key]?.jsonObject?.mapValues { (_, value) -> value.jsonPrimitive.float } ?: emptyMap()

        private fun JsonObject.getStringList(key: String): List<String> =
            this[key]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()

        private fun JsonObject.parseFloatList(key: String, default: List<Float>): List<Float> =
            this[key]?.let { element ->
                (element as? JsonArray)?.mapNotNull { entry -> entry.jsonPrimitive.floatOrNull }
            } ?: default

        private const val DEFAULT_INPUT_SIZE = 224
        private val DEFAULT_IMAGENET_MEAN = listOf(0.485f, 0.456f, 0.406f)
        private val DEFAULT_IMAGENET_STD = listOf(0.229f, 0.224f, 0.225f)
        private const val DEFAULT_INPUT_NAME = "input"
        private const val DEFAULT_OUTPUT_NAME = "output"
    }
}

internal enum class ImageModerationResizeMode {
    CENTER_CROP,
    STRETCH,
    ;

    companion object {
        fun fromConfigValue(value: String?): ImageModerationResizeMode =
            when (value?.lowercase()) {
                "stretch", "resize" -> STRETCH
                else -> CENTER_CROP
            }
    }
}
