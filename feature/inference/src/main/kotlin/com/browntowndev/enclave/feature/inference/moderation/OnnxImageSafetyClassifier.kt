package com.browntowndev.enclave.feature.inference.moderation

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.Log
import com.browntowndev.enclave.domain.model.config.UtilityType
import com.browntowndev.enclave.domain.port.inference.ImageModerationPort
import com.browntowndev.enclave.domain.port.inference.ImageModerationResult
import com.browntowndev.enclave.domain.port.repository.UtilityModelFilePort
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.exp

/**
 * ONNX-Runtime implementation of [ImageModerationPort] running a ViT-style image-safety classifier
 * on the CPU. The ONNX weights are downloaded on demand from R2 via [UtilityModelFilePort]; only
 * the small preprocessing config ships in APK assets. Loads lazily once (guarded by [loadMutex] and
 * [loadedModel]) and degrades
 * silently — any load or inference failure returns a non-blocking result with empty scores so the
 * generation pipeline is never broken by the moderation lane (per `INFERENCE_RULES.md §7`).
 */
@Singleton
class OnnxImageSafetyClassifier
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val utilityModelFilePort: UtilityModelFilePort,
    ) : ImageModerationPort {
        private val loadMutex = Mutex()

        @Volatile
        private var loadedModel: LoadedImageModerationModel? = null

        override suspend fun classify(imageBytes: ByteArray): ImageModerationResult =
            withContext(Dispatchers.IO) {
                runCatching {
                    val model = getLoadedModel()
                    if (model == null) {
                        Log.w(TAG, "Model failed to load; image passes unmoderated (${imageBytes.size} bytes)")
                        return@withContext notBlocked()
                    }
                    val scores = runInference(model, imageBytes)
                    if (scores == null) {
                        Log.w(TAG, "Inference returned null; image passes unmoderated (${imageBytes.size} bytes)")
                        return@withContext notBlocked()
                    }
                    buildResult(model.config, scores, imageBytes.size)
                }.getOrElse { e ->
                    Log.w(TAG, "Classification failed; image passes unmoderated (${imageBytes.size} bytes)", e)
                    notBlocked()
                }
            }

        private suspend fun getLoadedModel(): LoadedImageModerationModel? =
            loadedModel ?: loadMutex.withLock {
                loadedModel ?: runCatching { loadModel() }
                    .onFailure { e -> Log.w(TAG, "Failed to load image-safety model", e) }
                    .getOrNull()
                    ?.also { loadedModel = it }
            }

        private suspend fun loadModel(): LoadedImageModerationModel {
            val config = ImageModerationAssetConfig.parse(readAssetText(CONFIG_ASSET_PATH))
            val modelFile =
                resolveDownloadedModelFile()
                    ?: throw IllegalStateException(
                        "Image safety classifier is not downloaded (utilityType=${UtilityType.IMAGE_SAFETY_CLASSIFIER.apiValue})",
                    )
            val environment = OrtEnvironment.getEnvironment()
            val session =
                OrtSession.SessionOptions().use { sessionOptions ->
                    sessionOptions.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                    environment.createSession(modelFile.absolutePath, sessionOptions)
                }
            return LoadedImageModerationModel(config = config, session = session)
        }

        private fun runInference(
            model: LoadedImageModerationModel,
            imageBytes: ByteArray,
        ): FloatArray? {
            val tensorData = preprocess(imageBytes, model.config) ?: return null
            val shape = longArrayOf(1L, 3L, model.config.inputSize.toLong(), model.config.inputSize.toLong())
            val inputTensor =
                OnnxTensor.createTensor(
                    OrtEnvironment.getEnvironment(),
                    java.nio.FloatBuffer.wrap(tensorData),
                    shape,
                )
            val inputs = mapOf<String, OnnxTensor>(model.config.inputName to inputTensor)
            return try {
                model.session.run(inputs).use { results ->
                    val output = results[model.config.outputName].orElse(null)?.value ?: return null
                    when (output) {
                        is Array<*> -> (output.firstOrNull() as? FloatArray)
                        is FloatArray -> output
                        else -> null
                    }
                }
            } finally {
                inputTensor.close()
            }
        }

        /**
         * Decodes [imageBytes] into a [Bitmap], resizes-and-center-crops to [ImageModerationAssetConfig.inputSize],
         * normalizes channels with the configured mean/std, and emits an NCHW float32 tensor.
         */
        private fun preprocess(
            imageBytes: ByteArray,
            config: ImageModerationAssetConfig,
        ): FloatArray? {
            val source = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size) ?: return null
            val resized =
                when (config.resizeMode) {
                    ImageModerationResizeMode.STRETCH -> resizeToSize(source, config.inputSize)
                    ImageModerationResizeMode.CENTER_CROP -> centerCropToSize(source, config.inputSize)
                }
            val mean = config.channelMean
            val std = config.channelStd
            val size = config.inputSize
            val tensor = FloatArray(3 * size * size)
            for (y in 0 until size) {
                for (x in 0 until size) {
                    val pixel = resized.getPixel(x, y)
                    val r = Color.red(pixel) / CHANNEL_SCALE
                    val g = Color.green(pixel) / CHANNEL_SCALE
                    val b = Color.blue(pixel) / CHANNEL_SCALE
                    val spatial = y * size + x
                    tensor[spatial] = (r - mean[0]) / std[0]
                    tensor[size * size + spatial] = (g - mean[1]) / std[1]
                    tensor[2 * size * size + spatial] = (b - mean[2]) / std[2]
                }
            }
            if (resized !== source) resized.recycle()
            source.recycle()
            return tensor
        }

        private fun resizeToSize(source: Bitmap, size: Int): Bitmap =
            if (source.width == size && source.height == size) {
                source
            } else {
                Bitmap.createScaledBitmap(source, size, size, true)
            }

        private fun centerCropToSize(source: Bitmap, size: Int): Bitmap {
            val sourceWidth = source.width
            val sourceHeight = source.height
            val shortest = minOf(sourceWidth, sourceHeight)
            val scale = shortest.toFloat() / size.toFloat()
            val cropWidth = (size * scale).toInt().coerceAtLeast(1)
            val cropHeight = cropWidth
            val offsetX = ((sourceWidth - cropWidth) / 2).coerceAtLeast(0)
            val offsetY = ((sourceHeight - cropHeight) / 2).coerceAtLeast(0)
            val cropped =
                Bitmap.createBitmap(
                    source,
                    offsetX,
                    offsetY,
                    cropWidth.coerceAtMost(sourceWidth - offsetX),
                    cropHeight.coerceAtMost(sourceHeight - offsetY),
                )
            return if (cropped.width == size && cropped.height == size) {
                cropped
            } else {
                Bitmap.createScaledBitmap(cropped, size, size, true).also {
                    if (it !== cropped) cropped.recycle()
                }
            }
        }

        private fun buildResult(
            config: ImageModerationAssetConfig,
            logits: FloatArray,
            imageByteCount: Int,
        ): ImageModerationResult {
            if (logits.size != config.labels.size) {
                Log.w(
                    TAG,
                    "Logits length (${logits.size}) != labels length (${config.labels.size}); skipping moderation",
                )
                return notBlocked()
            }
            val probabilities = softmax(logits)
            val scoresByLabel =
                config.labels
                    .zip(probabilities.asList())
                    .associate { (label, score) -> label to score }
            val blocker =
                config.blockedLabels
                    .mapNotNull { label ->
                        val score = scoresByLabel[label] ?: return@mapNotNull null
                        val threshold = config.thresholds[label] ?: DEFAULT_THRESHOLD
                        if (score >= threshold) label to score else null
                    }.maxByOrNull { (_, score) -> score }
            val result =
                ImageModerationResult(
                    blocked = blocker != null,
                    scoresByLabel = scoresByLabel,
                    blockingLabel = blocker?.first,
                    threshold = blocker?.first?.let { config.thresholds[it] } ?: DEFAULT_THRESHOLD,
                )
            logClassificationResult(
                imageByteCount = imageByteCount,
                blockedLabels = config.blockedLabels,
                thresholds = config.thresholds,
                result = result,
            )
            return result
        }

        private fun logClassificationResult(
            imageByteCount: Int,
            blockedLabels: List<String>,
            thresholds: Map<String, Float>,
            result: ImageModerationResult,
        ) {
            val topLabel =
                result.scoresByLabel
                    .maxByOrNull { (_, score) -> score }
                    ?.let { (label, score) -> "$label=${formatScore(score)}" }
                    ?: "unknown"
            val scoreSummary =
                result.scoresByLabel.entries
                    .sortedByDescending { (_, score) -> score }
                    .joinToString(separator = ", ") { (label, score) -> "$label=${formatScore(score)}" }
            val thresholdSummary =
                blockedLabels.joinToString(separator = ", ") { label ->
                    val threshold = thresholds[label] ?: DEFAULT_THRESHOLD
                    val score = result.scoresByLabel[label]
                    if (score == null) {
                        "$label:threshold=${formatScore(threshold)},score=missing"
                    } else {
                        "$label:score=${formatScore(score)},threshold=${formatScore(threshold)}"
                    }
                }
            Log.i(
                TAG,
                "classified image ($imageByteCount bytes): top=$topLabel, blocked=${result.blocked}" +
                    (if (result.blocked) ", blockingLabel=${result.blockingLabel}" else "") +
                    ", scores={$scoreSummary}, blockCheck={$thresholdSummary}",
            )
        }

        private fun formatScore(score: Float): String = String.format(Locale.US, "%.4f", score)

        private fun notBlocked(): ImageModerationResult =
            ImageModerationResult(
                blocked = false,
                scoresByLabel = emptyMap(),
                blockingLabel = null,
                threshold = 0f,
            )

        private fun readAssetText(path: String): String =
            context.assets
                .open(path)
                .bufferedReader()
                .use { reader -> reader.readText() }

        private suspend fun resolveDownloadedModelFile(): File? {
            val resolvedPath =
                utilityModelFilePort.resolveUtilityModelPath(UtilityType.IMAGE_SAFETY_CLASSIFIER)
                    ?: return null
            val file = File(resolvedPath)
            return file.takeIf { it.exists() && it.isFile }
        }

        private data class LoadedImageModerationModel(
            val config: ImageModerationAssetConfig,
            val session: OrtSession,
        )

        private companion object {
            private const val TAG = "OnnxImageSafetyClassifier"
            private const val ASSET_BASE_PATH = "models/image_safety_classifier"
            private const val CONFIG_ASSET_PATH = "$ASSET_BASE_PATH/model_config.json"
            private const val CHANNEL_SCALE = 255.0f
            private const val DEFAULT_THRESHOLD = 0.5f
        }
    }

private fun softmax(logits: FloatArray): FloatArray {
    val max = logits.maxOrNull() ?: return FloatArray(0)
    val expValues = logits.map { logit -> exp((logit - max).toDouble()) }
    val sum = expValues.sum()
    return expValues.map { value -> (value / sum).toFloat() }.toFloatArray()
}
