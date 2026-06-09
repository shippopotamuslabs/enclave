package com.browntowndev.enclave.feature.inference.titlegen

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class T5UnigramTokenizerTest {
    private val tokenizerJson =
        """
        {
          "model": {
            "type": "Unigram",
            "unk_id": 2,
            "vocab": [
              ["<pad>", 0.0],
              ["</s>", 0.0],
              ["<unk>", 0.0],
              ["▁", -3.0],
              ["▁hello", -1.0],
              ["▁world", -1.0],
              ["▁chat", -1.0],
              ["▁title", -1.0]
            ]
          },
          "added_tokens": [
            { "id": 0, "content": "<pad>", "special": true },
            { "id": 1, "content": "</s>", "special": true },
            { "id": 2, "content": "<unk>", "special": true }
          ]
        }
        """.trimIndent()

    private val tokenizer = T5UnigramTokenizer.fromTokenizerJson(tokenizerJson)

    @Test
    fun `encode segments into unigram pieces and appends eos`() {
        val ids = tokenizer.encode("hello world", eosTokenId = 1, maxLength = 16)

        // ▁hello=4, ▁world=5, then eos=1
        assertEquals(listOf(4L, 5L, 1L), ids.toList())
    }

    @Test
    fun `decode restores spaces and drops special tokens`() {
        assertEquals("hello world", tokenizer.decode(listOf(4, 5)))
        assertEquals("hello world", tokenizer.decode(listOf(4, 5, 1)))
        assertEquals("chat title", tokenizer.decode(listOf(6, 7)))
    }

    @Test
    fun `encode respects maxLength including eos`() {
        val ids = tokenizer.encode("hello world chat title", eosTokenId = 1, maxLength = 3)

        assertEquals(3, ids.size)
        assertEquals(1L, ids.last())
    }

    @Test
    fun `round trip preserves known text`() {
        val ids = tokenizer.encode("chat title", eosTokenId = 1, maxLength = 16)
        val generated = ids.dropLast(1).map { it.toInt() } // drop eos as the decoder would
        assertEquals("chat title", tokenizer.decode(generated))
    }

    @Test
    fun `test real tokenizer serialization and encoding`() {
        val realJsonFile =
            listOf(
                java.io.File("build/chat_title_generator_dist/tokenizer.json"),
                java.io.File("../build/chat_title_generator_dist/tokenizer.json"),
            ).firstOrNull { it.exists() } ?: return
        val realTokenizer = T5UnigramTokenizer.fromTokenizerJson(realJsonFile.readText())
        val ids =
            realTokenizer.encode(
                "short title: What's the average person's maximum recoverable back volume per week",
                eosTokenId = 1,
                maxLength = 384,
            )
        val expected =
            listOf(710L, 2233L, 10L, 363L, 31L, 7L, 8L, 1348L, 568L, 31L, 7L, 2411L, 8303L, 179L, 223L, 2908L, 399L, 471L, 1L)
        assertEquals(expected, ids.toList())
    }
}

