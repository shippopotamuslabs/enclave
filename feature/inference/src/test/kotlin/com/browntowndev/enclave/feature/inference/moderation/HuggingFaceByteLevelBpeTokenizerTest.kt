package com.browntowndev.enclave.feature.inference.moderation

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HuggingFaceByteLevelBpeTokenizerTest {
    @Test
    fun encode_asciiPrompt_addsSpecialTokensAndPads() {
        val tokenizer =
            HuggingFaceByteLevelBpeTokenizer.fromTokenizerJson(
                tokenizerJson =
                    """
                    {
                      "model": {
                        "vocab": {
                          "[PAD]": 0,
                          "[CLS]": 1,
                          "[SEP]": 2,
                          "[UNK]": 3,
                          "H": 4,
                          "e": 5,
                          "l": 6,
                          "o": 7
                        },
                        "merges": []
                      }
                    }
                    """.trimIndent(),
                maxSequenceLength = 8,
            )

        val encoded = tokenizer.encode("Hello")

        assertArrayEquals(longArrayOf(1, 4, 5, 6, 6, 7, 2, 0), encoded.inputIds)
        assertArrayEquals(longArrayOf(1, 1, 1, 1, 1, 1, 1, 0), encoded.attentionMask)
        assertEquals(8, encoded.tokenTypeIds.size)
    }

    @Test
    fun encode_mergeablePrompt_appliesHighestPriorityBpeMerge() {
        val tokenizer =
            HuggingFaceByteLevelBpeTokenizer.fromTokenizerJson(
                tokenizerJson =
                    """
                    {
                      "model": {
                        "vocab": {
                          "[PAD]": 0,
                          "[CLS]": 1,
                          "[SEP]": 2,
                          "[UNK]": 3,
                          "H": 4,
                          "e": 5,
                          "He": 6
                        },
                        "merges": [
                          "H e"
                        ]
                      }
                    }
                    """.trimIndent(),
                maxSequenceLength = 4,
            )

        val encoded = tokenizer.encode("He")

        assertArrayEquals(longArrayOf(1, 6, 2, 0), encoded.inputIds)
    }
}
