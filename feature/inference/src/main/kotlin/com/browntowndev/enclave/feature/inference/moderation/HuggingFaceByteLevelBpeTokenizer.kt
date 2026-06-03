package com.browntowndev.enclave.feature.inference.moderation

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.ConcurrentHashMap

internal data class EncodedPrompt(
    val inputIds: LongArray,
    val attentionMask: LongArray,
    val tokenTypeIds: LongArray,
)

internal class HuggingFaceByteLevelBpeTokenizer(
    private val vocab: Map<String, Int>,
    merges: List<Pair<String, String>>,
    private val maxSequenceLength: Int,
) {
    private val mergeRanks = merges.withIndex().associate { (index, pair) -> pair to index }
    private val cache = ConcurrentHashMap<String, List<String>>()
    private val byteEncoder = byteEncoder()
    private val tokenPattern =
        Regex("""'s|'t|'re|'ve|'m|'ll|'d| ?\p{L}+| ?\p{N}+| ?[^\s\p{L}\p{N}]+|\s+(?!\S)|\s+""")

    private val padTokenId = vocab.getValue(PAD_TOKEN)
    private val clsTokenId = vocab.getValue(CLS_TOKEN)
    private val sepTokenId = vocab.getValue(SEP_TOKEN)
    private val unknownTokenId = vocab[UNKNOWN_TOKEN] ?: padTokenId

    fun encode(text: String): EncodedPrompt {
        val contentTokenIds =
            tokenPattern
                .findAll(text)
                .flatMap { match ->
                    val encodedToken = byteEncode(match.value)
                    bpe(encodedToken).map { piece -> vocab[piece] ?: unknownTokenId }
                }.take(maxSequenceLength - SPECIAL_TOKEN_COUNT)
                .toList()

        val unpaddedIds = listOf(clsTokenId) + contentTokenIds + sepTokenId
        val inputIds = LongArray(maxSequenceLength) { index -> unpaddedIds.getOrNull(index)?.toLong() ?: padTokenId.toLong() }
        val attentionMask = LongArray(maxSequenceLength) { index -> if (index < unpaddedIds.size) 1L else 0L }
        val tokenTypeIds = LongArray(maxSequenceLength)
        return EncodedPrompt(
            inputIds = inputIds,
            attentionMask = attentionMask,
            tokenTypeIds = tokenTypeIds,
        )
    }

    private fun byteEncode(token: String): String =
        token
            .encodeToByteArray()
            .joinToString(separator = "") { byte ->
                byteEncoder.getValue(byte.toInt() and BYTE_MASK).toString()
            }

    private fun bpe(token: String): List<String> =
        cache.getOrPut(token) {
            var word = token.map { it.toString() }
            while (word.size > 1) {
                val bestMerge =
                    word
                        .zipWithNext()
                        .withIndex()
                        .mapNotNull { (index, pair) ->
                            mergeRanks[pair]?.let { rank -> IndexedMerge(index = index, rank = rank) }
                        }.minByOrNull { merge -> merge.rank }
                        ?: break

                val nextWord = ArrayList<String>(word.size - 1)
                var index = 0
                while (index < word.size) {
                    if (index == bestMerge.index) {
                        nextWord += word[index] + word[index + 1]
                        index += 2
                    } else {
                        nextWord += word[index]
                        index += 1
                    }
                }
                word = nextWord
            }
            word
        }

    private data class IndexedMerge(
        val index: Int,
        val rank: Int,
    )

    companion object {
        private const val BYTE_MASK = 0xff
        private const val SPECIAL_TOKEN_COUNT = 2
        private const val PAD_TOKEN = "[PAD]"
        private const val CLS_TOKEN = "[CLS]"
        private const val SEP_TOKEN = "[SEP]"
        private const val UNKNOWN_TOKEN = "[UNK]"

        fun fromTokenizerJson(
            tokenizerJson: String,
            maxSequenceLength: Int,
        ): HuggingFaceByteLevelBpeTokenizer {
            val root = Json.parseToJsonElement(tokenizerJson).jsonObject
            val model = root.getValue("model").jsonObject
            val vocab =
                model
                    .getValue("vocab")
                    .jsonObject
                    .mapValues { (_, value) -> value.jsonPrimitive.int }
            val merges =
                model
                    .getValue("merges")
                    .jsonArray
                    .map { merge ->
                        when (merge) {
                            is JsonArray -> merge[0].jsonPrimitive.content to merge[1].jsonPrimitive.content
                            else -> {
                                val parts = merge.jsonPrimitive.content.split(" ")
                                require(parts.size == 2) { "Unsupported BPE merge entry: $merge" }
                                parts[0] to parts[1]
                            }
                        }
                    }
            return HuggingFaceByteLevelBpeTokenizer(
                vocab = vocab,
                merges = merges,
                maxSequenceLength = maxSequenceLength,
            )
        }

        private fun byteEncoder(): Map<Int, Char> {
            val bytes = mutableListOf<Int>()
            bytes += FIRST_VISIBLE_ASCII..LAST_VISIBLE_ASCII
            bytes += FIRST_LATIN_SUPPLEMENT_VISIBLE_RANGE_START..FIRST_LATIN_SUPPLEMENT_VISIBLE_RANGE_END
            bytes += SECOND_LATIN_SUPPLEMENT_VISIBLE_RANGE_START..BYTE_MAX
            val codePoints = bytes.toMutableList()
            var nextCodePoint = 0
            for (byte in BYTE_MIN..BYTE_MAX) {
                if (byte !in bytes) {
                    bytes += byte
                    codePoints += BYTE_ENCODER_CODE_POINT_OFFSET + nextCodePoint
                    nextCodePoint += 1
                }
            }
            return bytes.zip(codePoints).associate { (byte, codePoint) -> byte to codePoint.toChar() }
        }

        private const val BYTE_MIN = 0
        private const val BYTE_MAX = 255
        private const val FIRST_VISIBLE_ASCII = 33
        private const val LAST_VISIBLE_ASCII = 126
        private const val FIRST_LATIN_SUPPLEMENT_VISIBLE_RANGE_START = 161
        private const val FIRST_LATIN_SUPPLEMENT_VISIBLE_RANGE_END = 172
        private const val SECOND_LATIN_SUPPLEMENT_VISIBLE_RANGE_START = 174
        private const val BYTE_ENCODER_CODE_POINT_OFFSET = 256
    }
}
