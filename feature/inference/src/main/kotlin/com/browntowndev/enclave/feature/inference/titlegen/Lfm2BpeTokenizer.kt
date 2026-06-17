package com.browntowndev.enclave.feature.inference.titlegen

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.ConcurrentHashMap

/**
 * Byte-level BPE tokenizer for LiquidAI/LFM2.5-350M.
 *
 * Modelled on [com.browntowndev.enclave.feature.inference.moderation.HuggingFaceByteLevelBpeTokenizer]
 * but uses LFM2's pre-tokenizer regex and exposes a plain encode/decode API (no padding or
 * CLS/SEP tokens).
 */
internal class Lfm2BpeTokenizer(
    private val vocab: Map<String, Int>,
    merges: List<Pair<String, String>>,
    private val addedTokens: List<AddedToken>,
) {
    private val mergeRanks: Map<Pair<String, String>, Int> =
        merges.withIndex().associate { (index, pair) -> pair to index }
    private val bpeCache: ConcurrentHashMap<String, List<String>> = ConcurrentHashMap()
    private val byteEncoder: Map<Int, Char> = buildByteEncoder()
    private val byteDecoder: Map<Char, Byte> =
        byteEncoder.entries.associate { (byte, char) -> char to byte.toByte() }

    private val specialIds: Set<Int> =
        addedTokens.filter { token -> token.special }.map { token -> token.id }.toSet()

    private val idToVocab: Map<Int, String> =
        vocab.entries.associate { (piece, id) -> id to piece }

    /**
     * LFM2 pre-tokenizer pattern (from tokenizer.json `Regex` pre-tokenizer).
     *
     * Handles contractions, Unicode letter/number runs, punctuation clusters, and whitespace
     * collapsing exactly as the reference HuggingFace tokenizer does.
     */
    private val tokenPattern: Regex = Regex(
        """(?i:'s|'t|'re|'ve|'m|'ll|'d)|[^\r\n\p{L}\p{N}]?\p{L}+|\p{N}{1,3}| ?[^\s\p{L}\p{N}]+[\r\n]*|\s*[\r\n]+|\s+(?!\S)|\s+""",
    )

    /**
     * Encodes [text] to a list of vocabulary ids.
     *
     * No special tokens are prepended or appended; the caller is responsible for chat
     * template assembly.
     */
    fun encode(text: String): List<Int> =
        tokenPattern
            .findAll(text)
            .flatMap { match ->
                val byteEncoded = byteEncode(match.value)
                bpe(byteEncoded).mapNotNull { piece -> vocab[piece] }
            }.toList()

    /**
     * Decodes [ids] back to a UTF-8 string.
     *
     * Ids present in [addedTokens] with `special == true` are silently skipped so that
     * BOS, EOS, and chat template markers never appear in the returned string.
     */
    fun decode(ids: List<Int>): String {
        val bytes =
            ids
                .filter { id -> id !in specialIds }
                .flatMap { id ->
                    val piece = idToVocab[id] ?: return@flatMap emptyList()
                    piece.map { char -> byteDecoder[char] ?: char.code.toByte() }
                }.toByteArray()
        return bytes.toString(Charsets.UTF_8)
    }

    /**
     * Looks up the token id for the special token with the given [content] string.
     *
     * @throws IllegalArgumentException if no matching token is found in [addedTokens].
     */
    fun specialTokenId(content: String): Int =
        addedTokens.firstOrNull { token -> token.content == content }?.id
            ?: error("Special token not found: $content")

    private fun byteEncode(token: String): String =
        token
            .encodeToByteArray()
            .joinToString(separator = "") { byte ->
                byteEncoder.getValue(byte.toInt() and BYTE_MASK).toString()
            }

    private fun bpe(token: String): List<String> =
        bpeCache.getOrPut(token) {
            var word: List<String> = token.map { char -> char.toString() }
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

    internal data class AddedToken(
        val id: Int,
        val content: String,
        val special: Boolean,
    )

    companion object {
        private const val BYTE_MASK = 0xff

        /**
         * Constructs an [Lfm2BpeTokenizer] from the contents of a HuggingFace `tokenizer.json`.
         *
         * Parses `model.vocab` (object: piece→id), `model.merges` (array of strings or 2-element
         * arrays), and `added_tokens` (id, content, special).
         */
        fun fromTokenizerJson(json: String): Lfm2BpeTokenizer {
            val root = Json.parseToJsonElement(json).jsonObject
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
            val addedTokens =
                root["added_tokens"]
                    ?.jsonArray
                    ?.map { token ->
                        val obj = token.jsonObject
                        AddedToken(
                            id = obj.getValue("id").jsonPrimitive.int,
                            content = obj.getValue("content").jsonPrimitive.content,
                            special = obj["special"]?.jsonPrimitive?.boolean ?: false,
                        )
                    }
                    .orEmpty()
            return Lfm2BpeTokenizer(
                vocab = vocab,
                merges = merges,
                addedTokens = addedTokens,
            )
        }

        private fun buildByteEncoder(): Map<Int, Char> {
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
