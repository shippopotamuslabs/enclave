package com.browntowndev.enclave.feature.inference.titlegen

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.LongBuffer
import java.nio.file.Paths

/**
 * Desktop ORT smoke test for the exported T5 title generator (skipped when dist artifacts absent).
 */
class TitleGenOnnxIntegrationTest {
    @Test
    fun `greedy decode for hello prompt produces non-empty title`() {
        val encoder = resolveEncoder() ?: return
        val decoder = resolveDecoder() ?: return
        val tokenizerFile = resolveTokenizer() ?: return

        val config =
            TitleGenAssetConfig.parse(
                """
                {
                  "task_prefix": "short title: ",
                  "max_input_length": 384,
                  "max_output_tokens": 16,
                  "beam_width": 4,
                  "decoder_start_token_id": 0,
                  "eos_token_id": 1,
                  "pad_token_id": 0
                }
                """.trimIndent(),
            )
        val tokenizer = T5UnigramTokenizer.fromTokenizerJson(tokenizerFile.readText())
        val inputIds =
            tokenizer.encode(
                text = config.taskPrefix + "hello",
                eosTokenId = config.eosTokenId,
                maxLength = config.maxInputLength,
            )
        assertTrue(inputIds.isNotEmpty())

        val environment = OrtEnvironment.getEnvironment()
        val encoderSession =
            OrtSession.SessionOptions().use { options ->
                options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.NO_OPT)
                environment.createSession(encoder.absolutePath, options)
            }
        val decoderSession =
            OrtSession.SessionOptions().use { options ->
                options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.NO_OPT)
                environment.createSession(decoder.absolutePath, options)
            }
        encoderSession.use { encSession ->
            decoderSession.use { decSession ->
                val seqLen = inputIds.size
                val attentionMask = LongArray(seqLen) { 1L }
                val encoderInputs =
                    mapOf(
                        "input_ids" to
                            OnnxTensor.createTensor(
                                environment,
                                LongBuffer.wrap(inputIds),
                                longArrayOf(1, seqLen.toLong()),
                            ),
                        "attention_mask" to
                            OnnxTensor.createTensor(
                                environment,
                                LongBuffer.wrap(attentionMask),
                                longArrayOf(1, seqLen.toLong()),
                            ),
                    )
                try {
                    // The encoder Result must stay open for the whole decode loop:
                    // closing it releases the hidden-states tensor's native buffer
                    // (ORT then fails with "Unexpected input data type ... (((null)))").
                    encSession.run(encoderInputs).use { encoderResult ->
                        val hidden = encoderResult[0] as OnnxTensor
                        val encoderAttention =
                            OnnxTensor.createTensor(
                                environment,
                                LongBuffer.wrap(attentionMask),
                                longArrayOf(1, seqLen.toLong()),
                            )
                        try {
                            val generated =
                                TitleGenBeamDecoder.decode(config) { decoderIds ->
                                    val decoderTensor =
                                        OnnxTensor.createTensor(
                                            environment,
                                            LongBuffer.wrap(decoderIds),
                                            longArrayOf(1, decoderIds.size.toLong()),
                                        )
                                    decoderTensor.use { tensor ->
                                        val decoderInputs =
                                            mapOf(
                                                "input_ids" to tensor,
                                                "encoder_attention_mask" to encoderAttention,
                                                "encoder_hidden_states" to hidden,
                                            )
                                        decSession.run(decoderInputs).use { result ->
                                            lastStepLogits(result[0].value)
                                        }
                                    }
                                }
                            val decoded = tokenizer.decode(generated).trim()
                            assertFalse(decoded.isBlank())
                        } finally {
                            encoderAttention.close()
                        }
                    }
                } finally {
                    encoderInputs.values.forEach(OnnxTensor::close)
                }
            }
        }
    }

    private fun resolveEncoder(): File? =
        candidatePaths("build/chat_title_generator_dist/encoder_model_fp16.onnx")
            .firstOrNull { it.exists() }

    private fun resolveDecoder(): File? =
        candidatePaths("build/chat_title_generator_dist/decoder_model_fp16.onnx")
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