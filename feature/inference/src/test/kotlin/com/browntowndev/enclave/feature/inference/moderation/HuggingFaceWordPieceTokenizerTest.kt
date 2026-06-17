package com.browntowndev.enclave.feature.inference.moderation

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HuggingFaceWordPieceTokenizerTest {
    @Test
    fun encode_asciiPrompt_addsSpecialTokensAndPads() {
        val vocab =
            listOf(
                "[PAD]",
                "[CLS]",
                "[SEP]",
                "[UNK]",
                "hello",
                "world",
            )
        val tokenizer = HuggingFaceWordPieceTokenizer(vocab, maxSequenceLength = 8)

        val encoded = tokenizer.encode("hello world")

        assertArrayEquals(longArrayOf(1, 4, 5, 2, 0, 0, 0, 0), encoded.inputIds)
        assertArrayEquals(longArrayOf(1, 1, 1, 1, 0, 0, 0, 0), encoded.attentionMask)
        assertEquals(8, encoded.tokenTypeIds.size)
    }

    @Test
    fun encode_unknownWords_mapsToUnkToken() {
        val vocab =
            listOf(
                "[PAD]",
                "[CLS]",
                "[SEP]",
                "[UNK]",
                "hello",
            )
        val tokenizer = HuggingFaceWordPieceTokenizer(vocab, maxSequenceLength = 6)

        val encoded = tokenizer.encode("hello unknown")

        assertArrayEquals(longArrayOf(1, 4, 3, 2, 0, 0), encoded.inputIds)
        assertArrayEquals(longArrayOf(1, 1, 1, 1, 0, 0), encoded.attentionMask)
    }

    @Test
    fun encode_subwordSplitting_appliesDoubleHashPrefix() {
        val vocab =
            listOf(
                "[PAD]",
                "[CLS]",
                "[SEP]",
                "[UNK]",
                "play",
                "##ing",
            )
        val tokenizer = HuggingFaceWordPieceTokenizer(vocab, maxSequenceLength = 6)

        val encoded = tokenizer.encode("playing")

        assertArrayEquals(longArrayOf(1, 4, 5, 2, 0, 0), encoded.inputIds)
        assertArrayEquals(longArrayOf(1, 1, 1, 1, 0, 0), encoded.attentionMask)
    }

    @Test
    fun encode_punctuationAndLowercasing_handlesCorrectly() {
        val vocab =
            listOf(
                "[PAD]",
                "[CLS]",
                "[SEP]",
                "[UNK]",
                "hello",
                "!",
                "world",
            )
        val tokenizer = HuggingFaceWordPieceTokenizer(vocab, maxSequenceLength = 8)

        val encoded = tokenizer.encode("Hello, World!")

        // Hello -> hello, comma becomes punctuation token (which is mapped to UNK if not in vocab, or split),
        // world, !
        // cleanText separates Hello , World !
        // In our cleanText, split punctuation puts spaces around punctuation:
        // cleanText = "hello  ,   world  ! "
        // vocab: hello (4), , (UNK, 3), world (6), ! (5)
        // inputIds: cls (1), hello (4), , (UNK, 3), world (6), ! (5), sep (2), pad (0), pad (0)
        assertArrayEquals(longArrayOf(1, 4, 3, 6, 5, 2, 0, 0), encoded.inputIds)
    }
}
