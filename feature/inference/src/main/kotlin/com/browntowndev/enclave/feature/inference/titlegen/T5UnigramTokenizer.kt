package com.browntowndev.enclave.feature.inference.titlegen

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * SentencePiece **Unigram** tokenizer for T5 / flan-t5, built from a HuggingFace
 * `tokenizer.json`. Implements both encode (text → ids, via Viterbi over the unigram
 * log-probabilities) and decode (ids → text), which the existing in-repo tokenizers
 * (byte-level BPE, WordPiece) do not cover.
 *
 * Approximations vs. the reference tokenizer:
 * - The `Precompiled` normalizer charsmap is not replicated; input is only trimmed and
 *   has whitespace collapsed. This is adequate for short chat titles.
 * - Pre-tokenization uses the standard Metaspace scheme: spaces become `▁` (U+2581) and a
 *   leading `▁` is prepended (`add_prefix_space`).
 */
internal class T5UnigramTokenizer(
    private val pieceToId: Map<String, Int>,
    private val pieceScores: Map<String, Double>,
    private val idToPiece: Map<Int, String>,
    private val specialIds: Set<Int>,
    private val unkId: Int,
) {
    private val maxPieceLength: Int = pieceToId.keys.maxOfOrNull { it.length } ?: 1
    private val unkScore: Double = (pieceScores.values.minOrNull() ?: 0.0) - UNK_SCORE_MARGIN

    /**
     * Encodes [text] to input ids, appending [eosTokenId] (T5 terminates encoder input with
     * `</s>`). The result is capped at [maxLength] ids (including EOS).
     */
    fun encode(
        text: String,
        eosTokenId: Int,
        maxLength: Int,
    ): LongArray {
        val normalized = WHITESPACE.replace(text.trim(), " ")
        val pretokenized = METASPACE_STRING + normalized.replace(" ", METASPACE_STRING)
        val pieceIds = viterbi(pretokenized)
        val capped =
            if (pieceIds.size > maxLength - 1) pieceIds.subList(0, maxLength - 1) else pieceIds
        return LongArray(capped.size + 1) { index ->
            if (index < capped.size) capped[index].toLong() else eosTokenId.toLong()
        }
    }

    /** Decodes generated [ids] to text, dropping special tokens and restoring spaces. */
    fun decode(ids: List<Int>): String {
        val sb = StringBuilder()
        for (id in ids) {
            if (id !in specialIds) {
                idToPiece[id]?.let { piece -> sb.append(piece) }
            }
        }
        return sb
            .toString()
            .replace(METASPACE, ' ')
            .trim()
    }

    /** Viterbi best-path unigram segmentation over the metaspace-encoded [text]. */
    private fun viterbi(text: String): List<Int> {
        val n = text.length
        if (n == 0) return emptyList()
        val best = DoubleArray(n + 1) { Double.NEGATIVE_INFINITY }
        val backPiece = IntArray(n + 1) { -1 }
        val backStart = IntArray(n + 1) { -1 }
        best[0] = 0.0
        for (end in 1..n) {
            val minStart = maxOf(0, end - maxPieceLength)
            for (start in minStart until end) {
                val piece = text.substring(start, end)
                val id = pieceToId[piece]
                if (best[start] != Double.NEGATIVE_INFINITY && id != null) {
                    val score = best[start] + (pieceScores[piece] ?: unkScore)
                    if (score > best[end]) {
                        best[end] = score
                        backPiece[end] = id
                        backStart[end] = start
                    }
                }
            }
            // Single-character unknown fallback keeps the lattice connected.
            if (best[end] == Double.NEGATIVE_INFINITY && best[end - 1] != Double.NEGATIVE_INFINITY) {
                best[end] = best[end - 1] + unkScore
                backPiece[end] = unkId
                backStart[end] = end - 1
            }
        }
        val ids = ArrayDeque<Int>()
        var cursor = n
        while (cursor > 0) {
            val start = backStart[cursor]
            if (start < 0) break
            ids.addFirst(backPiece[cursor])
            cursor = start
        }
        return ids.toList()
    }

    companion object {
        private const val METASPACE = '▁'
        private const val METASPACE_STRING = "▁"
        private const val UNK_SCORE_MARGIN = 10.0
        private val WHITESPACE = Regex("\\s+")

        fun fromTokenizerJson(tokenizerJson: String): T5UnigramTokenizer {
            val root = Json.parseToJsonElement(tokenizerJson).jsonObject
            val model = root.getValue("model").jsonObject
            require(model["type"]?.jsonPrimitive?.content == "Unigram") {
                "Expected a Unigram tokenizer model"
            }
            val unkId = model["unk_id"]?.jsonPrimitive?.int ?: DEFAULT_UNK_ID

            val pieceToId = HashMap<String, Int>()
            val pieceScores = HashMap<String, Double>()
            val idToPiece = HashMap<Int, String>()
            model.getValue("vocab").jsonArray.forEachIndexed { index, entry ->
                val pair = entry as JsonArray
                val piece = pair[0].jsonPrimitive.content
                val score = pair[1].jsonPrimitive.doubleOrNull ?: 0.0
                pieceToId[piece] = index
                pieceScores[piece] = score
                idToPiece[index] = piece
            }

            // Special tokens are not emitted in decoded output. Pull them from added_tokens.
            val specialIds = HashSet<Int>()
            specialIds += unkId
            root["added_tokens"]?.jsonArray?.forEach { token ->
                val obj = token.jsonObject
                val id = obj["id"]?.jsonPrimitive?.int ?: return@forEach
                val isSpecial = obj["special"]?.jsonPrimitive?.booleanOrNull ?: false
                val content = obj["content"]?.jsonPrimitive?.content
                if (content != null && id !in idToPiece) idToPiece[id] = content
                if (isSpecial) specialIds += id
            }

            return T5UnigramTokenizer(
                pieceToId = pieceToId,
                pieceScores = pieceScores,
                idToPiece = idToPiece,
                specialIds = specialIds,
                unkId = unkId,
            )
        }

        private const val DEFAULT_UNK_ID = 2
    }
}
