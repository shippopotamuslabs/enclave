package com.browntowndev.enclave.feature.inference.titlegen

import kotlin.math.exp
import kotlin.math.ln

/**
 * Beam search decoder for the no-past T5 title-generator ONNX graphs.
 *
 * Width defaults to 4 — a cheap upgrade over greedy that helps some prompts without
 * changing the downloaded model. [ChatTitleSanitizer] still gates the final string.
 */
internal object TitleGenBeamDecoder {
    data class Hypothesis(
        val tokenIds: List<Int>,
        val decoderIds: List<Long>,
        val score: Double,
        val finished: Boolean,
    )

    fun decode(
        config: TitleGenAssetConfig,
        decodeStep: (decoderIds: LongArray) -> FloatArray?,
    ): List<Int> {
        val beamWidth = config.beamWidth.coerceAtLeast(1)
        if (beamWidth == 1) {
            return greedyDecode(config, decodeStep)
        }

        var beams =
            listOf(
                Hypothesis(
                    tokenIds = emptyList(),
                    decoderIds = listOf(config.decoderStartTokenId.toLong()),
                    score = 0.0,
                    finished = false,
                ),
            )

        repeat(config.maxOutputTokens) {
            val active = beams.filter { hypothesis -> !hypothesis.finished }
            if (active.isEmpty()) return@repeat

            val candidates = ArrayList<Hypothesis>()
            for (hypothesis in active) {
                val logits =
                    decodeStep(hypothesis.decoderIds.toLongArray()) ?: return hypothesis.tokenIds
                val logProbs = logSoftmax(logits)
                for (tokenId in topKIndices(logProbs, beamWidth)) {
                    val logProb = logProbs[tokenId].toDouble()
                    when (tokenId) {
                        config.eosTokenId ->
                            candidates +=
                                Hypothesis(
                                    tokenIds = hypothesis.tokenIds,
                                    decoderIds = hypothesis.decoderIds,
                                    score = hypothesis.score + logProb,
                                    finished = true,
                                )
                        config.padTokenId -> Unit
                        else ->
                            candidates +=
                                Hypothesis(
                                    tokenIds = hypothesis.tokenIds + tokenId,
                                    decoderIds = hypothesis.decoderIds + tokenId.toLong(),
                                    score = hypothesis.score + logProb,
                                    finished = false,
                                )
                    }
                }
            }

            if (candidates.isEmpty()) return@repeat

            val finishedBeams = beams.filter { hypothesis -> hypothesis.finished }
            beams =
                (finishedBeams + candidates)
                    .sortedByDescending { hypothesis -> hypothesis.score }
                    .take(beamWidth)

            if (beams.all { hypothesis -> hypothesis.finished }) return@repeat
        }

        return beams
            .filter { hypothesis -> hypothesis.finished }
            .maxByOrNull { hypothesis -> hypothesis.score }
            ?.tokenIds
            ?: beams.maxByOrNull { hypothesis -> hypothesis.score }?.tokenIds
            ?: emptyList()
    }

    private fun greedyDecode(
        config: TitleGenAssetConfig,
        decodeStep: (decoderIds: LongArray) -> FloatArray?,
    ): List<Int> {
        val generated = ArrayList<Int>()
        val decoderIds = ArrayList<Long>().apply { add(config.decoderStartTokenId.toLong()) }
        repeat(config.maxOutputTokens) {
            val logits = decodeStep(decoderIds.toLongArray()) ?: return generated
            val nextToken = argmax(logits)
            if (nextToken == config.eosTokenId || nextToken == config.padTokenId) {
                return generated
            }
            generated += nextToken
            decoderIds += nextToken.toLong()
        }
        return generated
    }
}

/** Stable log-softmax over finite logits; non-finite entries map to [Float.NEGATIVE_INFINITY]. */
internal fun logSoftmax(logits: FloatArray): FloatArray {
    if (logits.isEmpty()) return logits
    var max = Float.NEGATIVE_INFINITY
    for (value in logits) {
        if (!value.isNaN() && value > max) max = value
    }
    if (max == Float.NEGATIVE_INFINITY) {
        return FloatArray(logits.size) { Float.NEGATIVE_INFINITY }
    }
    var sum = 0.0
    for (value in logits) {
        if (!value.isNaN()) sum += exp((value - max).toDouble())
    }
    val logSum = max + ln(sum).toFloat()
    return FloatArray(logits.size) { index ->
        val value = logits[index]
        if (value.isNaN()) Float.NEGATIVE_INFINITY else value - logSum
    }
}

/** Indices of the [k] largest finite values in [values] (descending by score). */
internal fun topKIndices(
    values: FloatArray,
    k: Int,
): List<Int> {
    if (k <= 0 || values.isEmpty()) return emptyList()
    return values.indices
        .filter { index -> !values[index].isNaN() }
        .sortedByDescending { index -> values[index] }
        .take(k)
}
