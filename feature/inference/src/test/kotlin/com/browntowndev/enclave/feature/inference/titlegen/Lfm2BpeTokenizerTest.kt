package com.browntowndev.enclave.feature.inference.titlegen

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Paths

/**
 * Golden tests for [Lfm2BpeTokenizer] verified against the reference HuggingFace tokenizer.
 *
 * The tokenizer.json is loaded from `build/chat_title_generator_dist/tokenizer.json`
 * (resolved relative to the module directory). If the file is absent the test is skipped
 * rather than failed, matching the pattern used in [TitleGenOnnxIntegrationTest].
 */
class Lfm2BpeTokenizerTest {
    private fun loadTokenizer(): Lfm2BpeTokenizer? {
        val tokenizerFile =
            candidatePaths("build/chat_title_generator_dist/tokenizer.json")
                .firstOrNull { it.exists() }
                ?: return null
        return Lfm2BpeTokenizer.fromTokenizerJson(tokenizerFile.readText())
    }

    @Test
    fun `encode user newline matches golden`() {
        val tokenizer = loadTokenizer() ?: return
        assertEquals(listOf(6423, 708), tokenizer.encode("user\n"))
    }

    @Test
    fun `encode assistant newline matches golden`() {
        val tokenizer = loadTokenizer() ?: return
        assertEquals(listOf(64015, 708), tokenizer.encode("assistant\n"))
    }

    @Test
    fun `encode hello world matches golden`() {
        val tokenizer = loadTokenizer() ?: return
        assertEquals(listOf(36309, 521, 2031, 510), tokenizer.encode("Hello, world!"))
    }

    @Test
    fun `encode instruction string matches golden`() {
        val tokenizer = loadTokenizer() ?: return
        assertEquals(
            listOf(22998, 768, 730, 528, 522, 531, 2359, 5376),
            tokenizer.encode("Write a 3-6 word title"),
        )
    }

    @Test
    fun `encode spaces and newline matches golden`() {
        val tokenizer = loadTokenizer() ?: return
        assertEquals(listOf(730, 12914, 708, 3514, 1922), tokenizer.encode("  spaces\nnewline"))
    }

    @Test
    fun `encode unicode and emoji matches golden`() {
        val tokenizer = loadTokenizer() ?: return
        assertEquals(listOf(3517, 588, 3966, 23805, 746, 741, 1927), tokenizer.encode("émoji 🎉 test"))
    }

    @Test
    fun `full assembled prompt for hi with empty instruction matches golden`() {
        val tokenizer = loadTokenizer() ?: return
        val bosId = tokenizer.specialTokenId("<|startoftext|>")
        val imStartId = tokenizer.specialTokenId("<|im_start|>")
        val imEndId = tokenizer.specialTokenId("<|im_end|>")

        val promptIds =
            listOf(bosId, imStartId) +
                tokenizer.encode("user\n" + "" + "hi") +
                listOf(imEndId) +
                tokenizer.encode("\n") +
                listOf(imStartId) +
                tokenizer.encode("assistant\n")

        assertEquals(listOf(1, 6, 6423, 708, 6928, 7, 708, 6, 64015, 708), promptIds)
    }

    @Test
    fun `decode round-trip for hello world`() {
        val tokenizer = loadTokenizer() ?: return
        assertEquals("Hello, world!", tokenizer.decode(tokenizer.encode("Hello, world!")))
    }

    @Test
    fun `decode skips special token ids`() {
        val tokenizer = loadTokenizer() ?: return
        // BOS=1, EOS=7 are special; only the middle ids should decode to "Hello, world!"
        val ids = listOf(1, 36309, 521, 2031, 510, 7)
        assertEquals("Hello, world!", tokenizer.decode(ids))
    }

    private fun candidatePaths(relative: String): List<File> =
        listOf(
            Paths.get(relative),
            Paths.get("../$relative"),
            Paths.get("../../$relative"),
        ).map { it.toAbsolutePath().normalize().toFile() }
}
