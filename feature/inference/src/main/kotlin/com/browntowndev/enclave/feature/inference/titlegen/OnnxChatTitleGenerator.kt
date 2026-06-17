package com.browntowndev.enclave.feature.inference.titlegen

import android.content.Context
import com.browntowndev.enclave.domain.port.inference.ChatTitleGeneratorPort
import com.browntowndev.enclave.domain.port.repository.UtilityModelFilePort
import com.browntowndev.enclave.feature.inference.utilityonnx.Lfm2UtilityOnnxRunner
import com.browntowndev.enclave.feature.inference.utilityonnx.UtilityOnnxLoadCoordinator
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device chat-title generator backed by LiquidAI/LFM2.5-350M-ONNX (q4 variant).
 *
 * Part of the utility-ONNX lane (see INFERENCE_RULES.md §7): runs on [Dispatchers.IO],
 * never acquires the inference lock, and shares [UtilityOnnxLoadCoordinator] with compaction
 * summarization so only one utility graph is loaded at a time.
 */
@Singleton
class OnnxChatTitleGenerator
    @Inject
    constructor(
        @param:ApplicationContext context: Context,
        utilityModelFilePort: UtilityModelFilePort,
        loadCoordinator: UtilityOnnxLoadCoordinator,
    ) : ChatTitleGeneratorPort {
        private val runner =
            Lfm2UtilityOnnxRunner(
                context = context,
                utilityModelFilePort = utilityModelFilePort,
                loadCoordinator = loadCoordinator,
                logTag = TAG,
            )

        override suspend fun generateTitle(prompt: String): String? =
            runner.generate(configAssetPath = CONFIG_ASSET_PATH, userMessage = prompt)

        private companion object {
            private const val TAG = "OnnxChatTitleGen"
            private const val CONFIG_ASSET_PATH = "models/chat_title_generator/model_config.json"
        }
    }

// ---------------------------------------------------------------------------
// Shared greedy generation — used by Lfm2UtilityOnnxRunner and diagnostics.
// ---------------------------------------------------------------------------

/** Structured result returned by [runTitleGeneration]. */
internal data class GenerationDebug(
    val tokenIds: List<Int>,
    val rawDecode: String?,
    val outputShape: String?,
    val failureStage: String?,
    val failureDetail: String?,
)

/**
 * Assembles a ChatML prompt and runs greedy decoding on [session].
 *
 * The [userMessage] is truncated to `config.maxInputChars` characters before tokenisation.
 * Stops at [TitleGenAssetConfig.eosTokenId] or [TitleGenAssetConfig.maxOutputTokens].
 *
 * Session must already be open; the caller is responsible for closing it.
 */
internal fun runTitleGeneration(
    session: ai.onnxruntime.OrtSession,
    tokenizer: Lfm2BpeTokenizer,
    config: TitleGenAssetConfig,
    userMessage: String,
): GenerationDebug {
    val bosId = tokenizer.specialTokenId("<|startoftext|>")
    val imStartId = tokenizer.specialTokenId("<|im_start|>")
    val imEndId = tokenizer.specialTokenId("<|im_end|>")

    val truncatedMessage = userMessage.take(config.maxInputChars)

    val promptIds: LongArray =
        (
            listOf(bosId.toLong(), imStartId.toLong()) +
                tokenizer.encode("user\n" + config.instruction + truncatedMessage).map { it.toLong() } +
                listOf(imEndId.toLong()) +
                tokenizer.encode("\n").map { it.toLong() } +
                listOf(imStartId.toLong()) +
                tokenizer.encode("assistant\n").map { it.toLong() }
        ).toLongArray()

    val environment = ai.onnxruntime.OrtEnvironment.getEnvironment()
    val inputInfo = session.inputInfo

    val initialConvCaches: Map<String, ai.onnxruntime.OnnxTensor> =
        inputInfo
            .filterKeys { name -> name.startsWith("past_conv.") }
            .mapValues { (_, info) ->
                val shape = (info.info as ai.onnxruntime.TensorInfo).shape
                val dim1 = if (shape[1] < 0) CONV_DIM_FALLBACK else shape[1]
                val dim2 = if (shape[2] < 0) CONV_WINDOW_FALLBACK else shape[2]
                val size = (1L * dim1 * dim2).toInt()
                ai.onnxruntime.OnnxTensor.createTensor(
                    environment,
                    java.nio.FloatBuffer.wrap(FloatArray(size)),
                    longArrayOf(1L, dim1, dim2),
                )
            }

    val initialKvCaches: Map<String, ai.onnxruntime.OnnxTensor> =
        inputInfo
            .filterKeys { name ->
                name.startsWith("past_key_values.") &&
                    (name.endsWith(".key") || name.endsWith(".value"))
            }.mapValues { (_, info) ->
                val shape = (info.info as ai.onnxruntime.TensorInfo).shape
                val dim1 = if (shape[1] < 0) KV_HEADS_FALLBACK else shape[1]
                val dim3 = if (shape[3] < 0) KV_HEAD_DIM_FALLBACK else shape[3]
                ai.onnxruntime.OnnxTensor.createTensor(
                    environment,
                    java.nio.FloatBuffer.wrap(FloatArray(0)),
                    longArrayOf(1L, dim1, 0L, dim3),
                )
            }

    val numLogitsToKeepTensor: ai.onnxruntime.OnnxTensor =
        ai.onnxruntime.OnnxTensor.createTensor(
            environment,
            java.nio.LongBuffer.wrap(longArrayOf(1L)),
            longArrayOf(),
        )

    val generatedIds = ArrayList<Int>()
    var outputShape: String? = null

    var previousResult: ai.onnxruntime.OrtSession.Result? = null
    var convCaches: Map<String, ai.onnxruntime.OnnxTensor> = initialConvCaches
    var kvCaches: Map<String, ai.onnxruntime.OnnxTensor> = initialKvCaches

    try {
        var currentIds: LongArray = promptIds
        var totalSeqLen: Int = promptIds.size
        var step = 0
        var decoding = true

        while (decoding && step < config.maxOutputTokens) {
            val stepResult: ai.onnxruntime.OrtSession.Result? =
                runDecoderStep(
                    environment = environment,
                    session = session,
                    currentIds = currentIds,
                    totalSeqLen = totalSeqLen,
                    numLogitsToKeepTensor = numLogitsToKeepTensor,
                    convCaches = convCaches,
                    kvCaches = kvCaches,
                    step = step,
                )
            if (stepResult == null) {
                decoding = false
            } else {
                previousResult?.close()
                previousResult = stepResult

                val logitsValue: Any? = stepResult.get("logits")?.orElse(null)?.value ?: stepResult[0].value
                val logits: FloatArray? = lastStepLogits(logitsValue)
                when {
                    logits == null -> {
                        android.util.Log.w(
                            GENERATION_TAG,
                            "Decoder output not [1, seq, vocab] float logits: ${describeLogitsShape(logitsValue)}",
                        )
                        decoding = false
                    }
                    else -> {
                        outputShape = "[1, 1, ${logits.size}]"
                        if (step == 0) {
                            android.util.Log.d(GENERATION_TAG, "First-step logits: ${describeLogitsHealth(logits)}")
                        }
                        val nextToken = argmax(logits)
                        if (nextToken == config.eosTokenId) {
                            decoding = false
                        } else {
                            generatedIds += nextToken
                            currentIds = longArrayOf(nextToken.toLong())
                            totalSeqLen += 1
                            convCaches = extractCaches(inputInfo, stepResult, "past_conv.", "present_conv.")
                            kvCaches = extractCaches(inputInfo, stepResult, "past_key_values.", "present.")
                        }
                    }
                }
            }
            step += 1
        }
    } finally {
        previousResult?.close()
        initialConvCaches.values.forEach(ai.onnxruntime.OnnxTensor::close)
        initialKvCaches.values.forEach(ai.onnxruntime.OnnxTensor::close)
        numLogitsToKeepTensor.close()
    }

    if (generatedIds.isEmpty()) {
        android.util.Log.w(GENERATION_TAG, "Greedy decode produced no tokens (inputTokens=${promptIds.size})")
    }

    val rawDecode: String? =
        if (generatedIds.isEmpty()) {
            null
        } else {
            tokenizer.decode(generatedIds)
        }

    val stage: String? =
        when {
            generatedIds.isEmpty() -> "decode"
            rawDecode.isNullOrBlank() -> "decode"
            else -> null
        }
    val detail: String? =
        when {
            generatedIds.isEmpty() -> "No tokens generated before EOS or max steps"
            rawDecode.isNullOrBlank() -> "Decoded output blank after ${generatedIds.size} tokens"
            else -> null
        }

    return GenerationDebug(
        tokenIds = generatedIds,
        rawDecode = rawDecode,
        outputShape = outputShape,
        failureStage = stage,
        failureDetail = detail,
    )
}

internal fun createSession(
    environment: ai.onnxruntime.OrtEnvironment,
    modelPath: String,
): ai.onnxruntime.OrtSession =
    ai.onnxruntime.OrtSession.SessionOptions().use { sessionOptions ->
        sessionOptions.setOptimizationLevel(ai.onnxruntime.OrtSession.SessionOptions.OptLevel.ALL_OPT)
        environment.createSession(modelPath, sessionOptions)
    }

internal const val GENERATION_TAG = "OnnxChatTitleGen"

private const val CONV_DIM_FALLBACK = 1024L
private const val CONV_WINDOW_FALLBACK = 3L
private const val KV_HEADS_FALLBACK = 8L
private const val KV_HEAD_DIM_FALLBACK = 64L

@Suppress("LongParameterList")
private fun runDecoderStep(
    environment: ai.onnxruntime.OrtEnvironment,
    session: ai.onnxruntime.OrtSession,
    currentIds: LongArray,
    totalSeqLen: Int,
    numLogitsToKeepTensor: ai.onnxruntime.OnnxTensor,
    convCaches: Map<String, ai.onnxruntime.OnnxTensor>,
    kvCaches: Map<String, ai.onnxruntime.OnnxTensor>,
    step: Int,
): ai.onnxruntime.OrtSession.Result? {
    val inputIdsTensor =
        ai.onnxruntime.OnnxTensor.createTensor(
            environment,
            java.nio.LongBuffer.wrap(currentIds),
            longArrayOf(1L, currentIds.size.toLong()),
        )
    val attentionTensor =
        ai.onnxruntime.OnnxTensor.createTensor(
            environment,
            java.nio.LongBuffer.wrap(LongArray(totalSeqLen) { 1L }),
            longArrayOf(1L, totalSeqLen.toLong()),
        )
    val stepInputs =
        mutableMapOf<String, ai.onnxruntime.OnnxTensor>().also { map ->
            map["input_ids"] = inputIdsTensor
            map["attention_mask"] = attentionTensor
            map["num_logits_to_keep"] = numLogitsToKeepTensor
            map.putAll(convCaches)
            map.putAll(kvCaches)
        }
    return try {
        session.run(stepInputs)
    } catch (e: Exception) {
        android.util.Log.w(GENERATION_TAG, "Decoder step failed (step=$step)", e)
        null
    } finally {
        inputIdsTensor.close()
        attentionTensor.close()
    }
}

private fun extractCaches(
    inputInfo: Map<String, ai.onnxruntime.NodeInfo>,
    result: ai.onnxruntime.OrtSession.Result,
    pastPrefix: String,
    presentPrefix: String,
): Map<String, ai.onnxruntime.OnnxTensor> =
    inputInfo
        .filterKeys { name -> name.startsWith(pastPrefix) }
        .mapValues { (inputName, _) ->
            val outputName = inputName.replace(pastPrefix, presentPrefix)
            result.get(outputName)?.orElse(null) as? ai.onnxruntime.OnnxTensor
                ?: error("Missing cache output: $outputName")
        }

internal fun describeLogitsHealth(logits: FloatArray): String {
    var nan = 0
    var posInf = 0
    var negInf = 0
    for (value in logits) {
        when {
            value.isNaN() -> nan++
            value == Float.POSITIVE_INFINITY -> posInf++
            value == Float.NEGATIVE_INFINITY -> negInf++
        }
    }
    val top5 =
        logits.indices
            .sortedByDescending { index -> if (logits[index].isNaN()) Float.NEGATIVE_INFINITY else logits[index] }
            .take(5)
            .joinToString { index -> "$index:${logits[index]}" }
    return "vocab=${logits.size} nan=$nan +inf=$posInf -inf=$negInf top5=[$top5]"
}

internal fun describeLogitsShape(value: Any?): String =
    when (value) {
        null -> "null"
        is Array<*> -> "Array<${describeLogitsShape(value.firstOrNull())}>(size=${value.size})"
        else -> value.javaClass.simpleName
    }

internal fun lastStepLogits(value: Any?): FloatArray? {
    val batch = value as? Array<*> ?: return null
    val steps = batch.firstOrNull() as? Array<*> ?: return null
    return steps.lastOrNull() as? FloatArray
}

internal fun argmax(row: FloatArray): Int {
    var bestIndex = 0
    var bestValue = row[0]
    for (index in 1 until row.size) {
        if (row[index] > bestValue) {
            bestValue = row[index]
            bestIndex = index
        }
    }
    return bestIndex
}
