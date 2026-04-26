package com.browntowndev.pocketcrew.core.data.tokenizer

import java.io.File
import java.text.Normalizer

class KotlinWordPieceTokenizer(vocabFile: File) {
    private val vocab: Map<String, Int> = vocabFile.readLines()
        .mapIndexed { index, s -> s to index }
        .toMap()

    private val UNK_TOKEN = "[UNK]"
    private val CLS_TOKEN = "[CLS]"
    private val SEP_TOKEN = "[SEP]"
    private val PAD_TOKEN = "[PAD]"

    private val unkId = vocab[UNK_TOKEN] ?: 100
    private val clsId = vocab[CLS_TOKEN] ?: 101
    private val sepId = vocab[SEP_TOKEN] ?: 102
    private val padId = vocab[PAD_TOKEN] ?: 0

    fun tokenize(text: String, maxLength: Int = 256): TokenizationResult {
        val cleanText = cleanText(text)
        val tokens = mutableListOf<String>()
        
        for (word in cleanText.split(Regex("\\s+"))) {
            if (word.isEmpty()) continue
            
            var start = 0
            while (start < word.length) {
                var end = word.length
                var curSubword: String? = null
                while (start < end) {
                    var subword = word.substring(start, end)
                    if (start > 0) subword = "##$subword"
                    if (vocab.containsKey(subword)) {
                        curSubword = subword
                        break
                    }
                    end--
                }
                if (curSubword == null) {
                    tokens.add(UNK_TOKEN)
                    break
                }
                tokens.add(curSubword)
                start = end
            }
        }

        val limitedTokens = if (tokens.size > maxLength - 2) tokens.subList(0, maxLength - 2) else tokens
        val inputIds = LongArray(maxLength) { padId.toLong() }
        val attentionMask = LongArray(maxLength) { 0L }
        val tokenTypeIds = LongArray(maxLength) { 0L }

        inputIds[0] = clsId.toLong()
        attentionMask[0] = 1L
        
        for (i in limitedTokens.indices) {
            inputIds[i + 1] = (vocab[limitedTokens[i]] ?: unkId).toLong()
            attentionMask[i + 1] = 1L
        }
        
        inputIds[limitedTokens.size + 1] = sepId.toLong()
        attentionMask[limitedTokens.size + 1] = 1L

        return TokenizationResult(inputIds, attentionMask, tokenTypeIds)
    }

    private fun cleanText(text: String): String {
        // Lowercase
        var clean = text.lowercase()
        // Strip accents/diacritics
        clean = Normalizer.normalize(clean, Normalizer.Form.NFD)
        clean = clean.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        // Split punctuation
        clean = clean.replace(Regex("([\\p{Punct}])"), " $1 ")
        return clean
    }

    data class TokenizationResult(
        val inputIds: LongArray,
        val attentionMask: LongArray,
        val tokenTypeIds: LongArray
    )
}
