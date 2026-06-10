package com.browntowndev.enclave.feature.inference.titlegen

import ai.onnxruntime.NodeInfo
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.content.Context
import android.util.Log
import com.browntowndev.enclave.domain.model.config.UtilityType
import com.browntowndev.enclave.domain.port.inference.ChatTitleGeneratorPort
import com.browntowndev.enclave.domain.port.repository.UtilityModelFilePort
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device chat-title generator backed by LiquidAI/LFM2.5-350M-ONNX (q4 variant).
 *
 * Part of the utility-ONNX lane (see INFERENCE_RULES.md §7): runs on [Dispatchers.IO],
 * never acquires the inference lock (so it executes in parallel with the chat LLM on the
 * CPU execution provider), and degrades silently to `null` on any failure.
 *
 * The model is decoder-only with ChatML prompt format and byte-level BPE tokenization.
 * Decoding uses greedy argmax with KV+conv caches. Sessions are NOT cached between calls
 * because the 350MB weights make RAM cost dominate over the ~0.3 s load time; only the
 * parsed tokenizer and config are kept in memory.
 *
 * The q4 graph uses int4 weights with fp32 activations. Full fp16 compute produces NaN
 * logits on ARM NEON (fp16 accumulation overflows for this model's activation scale) —
 * keep ALL_OPT which constant-folds Cast nodes but stays on fp32 arithmetic.
 */
@Singleton
class OnnxChatTitleGenerator
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val utilityModelFilePort: UtilityModelFilePort,
    ) : ChatTitleGeneratorPort {
        private val cacheMutex: Mutex = Mutex()

        @Volatile
        private var cachedTokenizer: Lfm2BpeTokenizer? = null

        @Volatile
        private var cachedConfig: TitleGenAssetConfig? = null

        override suspend fun generateTitle(prompt: String): String? =
            withContext(Dispatchers.IO) {
                if (prompt.isBlank()) return@withContext null
                try {
                    val graphPath =
                        utilityModelFilePort.resolveUtilityModelPath(UtilityType.TITLE_GENERATOR_ENCODER)
                    val dataPath =
                        utilityModelFilePort.resolveUtilityModelPath(UtilityType.TITLE_GENERATOR_DECODER)
                    val tokenizerPath =
                        utilityModelFilePort.resolveUtilityModelPath(UtilityType.TITLE_GENERATOR_TOKENIZER)
                    if (graphPath == null || dataPath == null || tokenizerPath == null) {
                        Log.w(
                            TAG,
                            "Title model unresolved (missing file or checksum mismatch): " +
                                "encoder=${graphPath != null} decoder=${dataPath != null} " +
                                "tokenizer=${tokenizerPath != null}",
                        )
                        return@withContext null
                    }

                    val config = getConfig()
                    val tokenizer = getTokenizer(tokenizerPath)

                    val environment = OrtEnvironment.getEnvironment()
                    // Sessions are loaded per-call; 350 MB weights — RAM matters more than 0.3 s load.
                    val session = createSession(environment = environment, modelPath = graphPath)
                    try {
                        val result =
                            runTitleGeneration(
                                session = session,
                                tokenizer = tokenizer,
                                config = config,
                                userMessage = prompt,
                            )
                        result.rawDecode?.trim()?.takeIf { it.isNotBlank() }
                    } finally {
                        session.close()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Title generation failed; falling back", e)
                    null
                }
            }

        private suspend fun getConfig(): TitleGenAssetConfig =
            cachedConfig ?: cacheMutex.withLock {
                cachedConfig ?: TitleGenAssetConfig.parse(readAssetText(CONFIG_ASSET_PATH))
                    .also { cachedConfig = it }
            }

        private suspend fun getTokenizer(tokenizerPath: String): Lfm2BpeTokenizer =
            cachedTokenizer ?: cacheMutex.withLock {
                cachedTokenizer ?: Lfm2BpeTokenizer.fromTokenizerJson(File(tokenizerPath).readText())
                    .also { cachedTokenizer = it }
            }

        private fun readAssetText(path: String): String =
            context.assets.open(path).bufferedReader().use { reader -> reader.readText() }

        private companion object {
            private const val TAG = "OnnxChatTitleGen"
            private const val CONFIG_ASSET_PATH = "models/chat_title_generator/model_config.json"
        }
    }

// ---------------------------------------------------------------------------
// Shared greedy generation — used by OnnxChatTitleGenerator and
// TitleGenHarnessDiagnostics to avoid duplicating the decode loop.
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
    session: OrtSession,
    tokenizer: Lfm2BpeTokenizer,
    config: TitleGenAssetConfig,
    userMessage: String,
): GenerationDebug {
    val bosId = tokenizer.specialTokenId("<|startoftext|>")
    val imStartId = tokenizer.specialTokenId("<|im_start|>")
    val imEndId = tokenizer.specialTokenId("<|im_end|>")

    val truncatedMessage = userMessage.take(config.maxInputChars)

    // ChatML prompt:
    // [BOS, IM_START] + encode("user\n" + instruction + message) + [IM_END]
    // + encode("\n") + [IM_START] + encode("assistant\n")
    val promptIds: LongArray =
        (
            listOf(bosId.toLong(), imStartId.toLong()) +
                tokenizer.encode("user\n" + config.instruction + truncatedMessage).map { it.toLong() } +
                listOf(imEndId.toLong()) +
                tokenizer.encode("\n").map { it.toLong() } +
                listOf(imStartId.toLong()) +
                tokenizer.encode("assistant\n").map { it.toLong() }
        ).toLongArray()

    val environment = OrtEnvironment.getEnvironment()
    val inputInfo = session.inputInfo

    // Build initial zero tensors for conv and KV caches using dims from session metadata.
    val initialConvCaches: Map<String, OnnxTensor> =
        inputInfo
            .filterKeys { name -> name.startsWith("past_conv.") }
            .mapValues { (_, info) ->
                val shape = (info.info as TensorInfo).shape
                // shape: [batch, dim1, dim2]; dynamic dims are -1 → use 1 for batch, keep others
                val dim1 = if (shape[1] < 0) CONV_DIM_FALLBACK else shape[1]
                val dim2 = if (shape[2] < 0) CONV_WINDOW_FALLBACK else shape[2]
                val size = (1L * dim1 * dim2).toInt()
                OnnxTensor.createTensor(
                    environment,
                    FloatBuffer.wrap(FloatArray(size)),
                    longArrayOf(1L, dim1, dim2),
                )
            }

    val initialKvCaches: Map<String, OnnxTensor> =
        inputInfo
            .filterKeys { name ->
                name.startsWith("past_key_values.") &&
                    (name.endsWith(".key") || name.endsWith(".value"))
            }.mapValues { (_, info) ->
                val shape = (info.info as TensorInfo).shape
                // shape: [batch, heads, past_seq, head_dim]; past_seq starts at 0
                val dim1 = if (shape[1] < 0) KV_HEADS_FALLBACK else shape[1]
                val dim3 = if (shape[3] < 0) KV_HEAD_DIM_FALLBACK else shape[3]
                OnnxTensor.createTensor(
                    environment,
                    FloatBuffer.wrap(FloatArray(0)),
                    longArrayOf(1L, dim1, 0L, dim3),
                )
            }

    // num_logits_to_keep scalar = 1 (only last-step logits needed)
    val numLogitsToKeepTensor: OnnxTensor =
        OnnxTensor.createTensor(
            environment,
            LongBuffer.wrap(longArrayOf(1L)),
            longArrayOf(),
        )

    val generatedIds = ArrayList<Int>()
    var outputShape: String? = null

    // previousResult is kept open until after the next step's input tensors are extracted,
    // so that ORT's native buffers remain valid across steps.
    var previousResult: OrtSession.Result? = null
    // Mutable cache maps — replaced each step with tensors referencing the new result.
    var convCaches: Map<String, OnnxTensor> = initialConvCaches
    var kvCaches: Map<String, OnnxTensor> = initialKvCaches

    try {
        var currentIds: LongArray = promptIds
        var totalSeqLen: Int = promptIds.size
        var step = 0
        var decoding = true

        while (decoding && step < config.maxOutputTokens) {
            val stepResult: OrtSession.Result? =
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
                // Close the previous result now that this step's result is available.
                previousResult?.close()
                previousResult = stepResult

                val logitsValue: Any? = stepResult.get("logits")?.orElse(null)?.value ?: stepResult[0].value
                // shape [1, 1, 65536] — num_logits_to_keep=1 so only one row
                val logits: FloatArray? = lastStepLogits(logitsValue)
                when {
                    logits == null -> {
                        Log.w(
                            GENERATION_TAG,
                            "Decoder output not [1, seq, vocab] float logits: ${describeLogitsShape(logitsValue)}",
                        )
                        decoding = false
                    }
                    else -> {
                        outputShape = "[1, 1, ${logits.size}]"
                        if (step == 0) {
                            Log.d(GENERATION_TAG, "First-step logits: ${describeLogitsHealth(logits)}")
                        }
                        val nextToken = argmax(logits)
                        if (nextToken == config.eosTokenId) {
                            decoding = false
                        } else {
                            generatedIds += nextToken
                            // Next step: single new token id, total seq grows by 1.
                            currentIds = longArrayOf(nextToken.toLong())
                            totalSeqLen += 1
                            // Cache tensors are owned by stepResult and stay valid until it is
                            // closed (after the next run); map output names back to input names:
                            // present_conv.X → past_conv.X, present.X.key → past_key_values.X.key.
                            convCaches = extractCaches(inputInfo, stepResult, "past_conv.", "present_conv.")
                            kvCaches = extractCaches(inputInfo, stepResult, "past_key_values.", "present.")
                        }
                    }
                }
            }
            step += 1
        }
    } finally {
        // Close the last result and the initial zero-cache tensors.
        previousResult?.close()
        initialConvCaches.values.forEach(OnnxTensor::close)
        initialKvCaches.values.forEach(OnnxTensor::close)
        numLogitsToKeepTensor.close()
    }

    if (generatedIds.isEmpty()) {
        Log.w(GENERATION_TAG, "Greedy decode produced no tokens (inputTokens=${promptIds.size})")
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

/**
 * Creates an ONNX session for a model at [modelPath].
 *
 * The q4 graph computes int4 weights ([MatMulNBits]) against fp32 activations. Never swap
 * in an fp16-compute variant: ORT's ARM NEON fp16 kernels accumulate in fp16 and produce
 * all-NaN logits (this killed the previous T5 export).
 */
internal fun createSession(
    environment: OrtEnvironment,
    modelPath: String,
): OrtSession =
    OrtSession.SessionOptions().use { sessionOptions ->
        sessionOptions.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        environment.createSession(modelPath, sessionOptions)
    }

// ---------------------------------------------------------------------------
// Decode helpers (also used by diagnostics and tests)
// ---------------------------------------------------------------------------

internal const val GENERATION_TAG = "OnnxChatTitleGen"

// Fallbacks for dynamic cache dims; static in the shipped LFM2.5-350M graph.
private const val CONV_DIM_FALLBACK = 1024L
private const val CONV_WINDOW_FALLBACK = 3L
private const val KV_HEADS_FALLBACK = 8L
private const val KV_HEAD_DIM_FALLBACK = 64L

/**
 * Runs one decoder forward pass. Returns the open [OrtSession.Result] (caller owns closing
 * it), or null after logging if the run fails. Per-step input tensors are closed here;
 * cache tensors are owned by the previous step's result and are not touched.
 */
@Suppress("LongParameterList")
private fun runDecoderStep(
    environment: OrtEnvironment,
    session: OrtSession,
    currentIds: LongArray,
    totalSeqLen: Int,
    numLogitsToKeepTensor: OnnxTensor,
    convCaches: Map<String, OnnxTensor>,
    kvCaches: Map<String, OnnxTensor>,
    step: Int,
): OrtSession.Result? {
    val inputIdsTensor =
        OnnxTensor.createTensor(
            environment,
            LongBuffer.wrap(currentIds),
            longArrayOf(1L, currentIds.size.toLong()),
        )
    val attentionTensor =
        OnnxTensor.createTensor(
            environment,
            LongBuffer.wrap(LongArray(totalSeqLen) { 1L }),
            longArrayOf(1L, totalSeqLen.toLong()),
        )
    val stepInputs =
        mutableMapOf<String, OnnxTensor>().also { map ->
            map["input_ids"] = inputIdsTensor
            map["attention_mask"] = attentionTensor
            map["num_logits_to_keep"] = numLogitsToKeepTensor
            map.putAll(convCaches)
            map.putAll(kvCaches)
        }
    return try {
        session.run(stepInputs)
    } catch (e: Exception) {
        Log.w(GENERATION_TAG, "Decoder step failed (step=$step)", e)
        null
    } finally {
        // Inputs are only needed during run(); cache tensors stay open via their result.
        inputIdsTensor.close()
        attentionTensor.close()
    }
}

/** Maps cache outputs of [result] back to past-input names ([pastPrefix] ← [presentPrefix]). */
private fun extractCaches(
    inputInfo: Map<String, NodeInfo>,
    result: OrtSession.Result,
    pastPrefix: String,
    presentPrefix: String,
): Map<String, OnnxTensor> =
    inputInfo
        .filterKeys { name -> name.startsWith(pastPrefix) }
        .mapValues { (inputName, _) ->
            val outputName = inputName.replace(pastPrefix, presentPrefix)
            result.get(outputName)?.orElse(null) as? OnnxTensor
                ?: error("Missing cache output: $outputName")
        }

/** NaN/inf counts and top-5 (index:value) pairs for a logits row, for diagnostics. */
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

/** Human-readable description of an unexpected decoder output value, for diagnostics. */
internal fun describeLogitsShape(value: Any?): String =
    when (value) {
        null -> "null"
        is Array<*> -> "Array<${describeLogitsShape(value.firstOrNull())}>(size=${value.size})"
        else -> value.javaClass.simpleName
    }

/** Returns the logits for the final decode step from a `[1, seq, vocab]` output, or null. */
internal fun lastStepLogits(value: Any?): FloatArray? {
    val batch = value as? Array<*> ?: return null
    val steps = batch.firstOrNull() as? Array<*> ?: return null
    return steps.lastOrNull() as? FloatArray
}

/** Index of the maximum value in [row]. */
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
