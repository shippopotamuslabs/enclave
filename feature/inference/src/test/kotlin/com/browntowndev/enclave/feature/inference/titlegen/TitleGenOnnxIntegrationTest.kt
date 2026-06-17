package com.browntowndev.enclave.feature.inference.titlegen

import ai.onnxruntime.OrtEnvironment
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Paths

/**
 * Desktop ORT smoke test for the exported LFM2 title generator (skipped when dist artifacts absent).
 *
 * Requires `build/chat_title_generator_dist/model_q4.onnx`, `model_q4.onnx_data`, and
 * `tokenizer.json` to all be present. The .onnx_data file must live next to the graph so ORT
 * can find it via the relative external-data path baked into the .onnx file.
 */
class TitleGenOnnxIntegrationTest {
    @Test
    fun `greedy decode for sourdough prompt produces non-blank title`() {
        val graphFile = resolveGraph() ?: return
        val dataFile = resolveData() ?: return
        val tokenizerFile = resolveTokenizer() ?: return

        // The .onnx_data file must exist next to the graph — ORT locates it automatically.
        check(dataFile.parentFile?.canonicalPath == graphFile.parentFile?.canonicalPath) {
            "model_q4.onnx_data must be in the same directory as model_q4.onnx"
        }

        val config =
            TitleGenAssetConfig.parse(
                """
                {
                  "eos_token_id": 7,
                  "instruction": "Write a 3-6 word title summarizing the topic of this message. Respond with the title and nothing else.\n\nMessage: ",
                  "max_input_chars": 1500,
                  "max_output_tokens": 24
                }
                """.trimIndent(),
            )
        val tokenizer = Lfm2BpeTokenizer.fromTokenizerJson(tokenizerFile.readText())

        val environment = OrtEnvironment.getEnvironment()
        val session = createSession(environment = environment, modelPath = graphFile.absolutePath)
        session.use { openSession ->
            val generation =
                runTitleGeneration(
                    session = openSession,
                    tokenizer = tokenizer,
                    config = config,
                    userMessage = "How do I bake sourdough bread at home?",
                )
            println("Integration test title: '${generation.rawDecode}'")
            assertFalse(generation.rawDecode.isNullOrBlank(), "Expected a non-blank title")
        }
    }

    private fun resolveGraph(): File? =
        candidatePaths("build/chat_title_generator_dist/model_q4.onnx")
            .firstOrNull { it.exists() }

    private fun resolveData(): File? =
        candidatePaths("build/chat_title_generator_dist/model_q4.onnx_data")
            .firstOrNull { it.exists() }

    private fun resolveTokenizer(): File? =
        candidatePaths("build/chat_title_generator_dist/tokenizer.json")
            .firstOrNull { it.exists() }

    private fun candidatePaths(relative: String): List<File> =
        listOf(
            Paths.get(relative),
            Paths.get("../$relative"),
            Paths.get("../../$relative"),
        ).map { it.toAbsolutePath().normalize().toFile() }
}
