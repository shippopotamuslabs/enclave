package com.browntowndev.enclave.domain.model.download

import com.browntowndev.enclave.domain.model.config.UtilityType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LocalFileNameDisambiguatorTest {
    @Test
    fun `resolveLocalFileName keeps unique catalog filenames unchanged`() {
        val catalog =
            listOf(
                CatalogFileIdentity(
                    fileName = "ggml-base.en.bin",
                    sha256 = "whisper-sha",
                    utilityType = UtilityType.WHISPER,
                ),
            )

        val resolved =
            LocalFileNameDisambiguator.resolveLocalFileName(
                identity = catalog.first(),
                catalog = catalog,
            )

        assertEquals("ggml-base.en.bin", resolved)
    }

    @Test
    fun `resolveLocalFileName keeps shared filename when SHA matches`() {
        val sharedSha = "shared-zip-sha"
        val catalog =
            listOf(
                CatalogFileIdentity(
                    fileName = "pack.zip",
                    sha256 = sharedSha,
                    huggingFacePath = "sdxl",
                ),
                CatalogFileIdentity(
                    fileName = "pack.zip",
                    sha256 = sharedSha,
                    huggingFacePath = "sdxl",
                    utilityType = UtilityType.SDXL_QNN_PACK,
                ),
            )

        catalog.forEach { identity ->
            assertEquals(
                "pack.zip",
                LocalFileNameDisambiguator.resolveLocalFileName(identity, catalog),
            )
        }
    }

    @Test
    fun `resolveLocalFileName prefixes colliding safety classifier filenames`() {
        val catalog =
            listOf(
                CatalogFileIdentity(
                    fileName = "model_quantized.onnx",
                    sha256 = "image-sha",
                    huggingFacePath = "image_safety_classifier",
                    utilityType = UtilityType.IMAGE_SAFETY_CLASSIFIER,
                ),
                CatalogFileIdentity(
                    fileName = "model_quantized.onnx",
                    sha256 = "prompt-sha",
                    huggingFacePath = "nsfw_text_classifier",
                    utilityType = UtilityType.PROMPT_SAFETY_CLASSIFIER,
                ),
            )

        assertEquals(
            "image_safety_classifier_model_quantized.onnx",
            LocalFileNameDisambiguator.resolveLocalFileName(catalog[0], catalog),
        )
        assertEquals(
            "nsfw_text_classifier_model_quantized.onnx",
            LocalFileNameDisambiguator.resolveLocalFileName(catalog[1], catalog),
        )
    }

    @Test
    fun `orphanedLegacyFilenames flags legacy shared safety classifier path`() {
        val catalog =
            listOf(
                CatalogFileIdentity(
                    fileName = "model_quantized.onnx",
                    sha256 = "image-sha",
                    huggingFacePath = "image_safety_classifier",
                    utilityType = UtilityType.IMAGE_SAFETY_CLASSIFIER,
                ),
                CatalogFileIdentity(
                    fileName = "model_quantized.onnx",
                    sha256 = "prompt-sha",
                    huggingFacePath = "nsfw_text_classifier",
                    utilityType = UtilityType.PROMPT_SAFETY_CLASSIFIER,
                ),
            )

        assertEquals(
            setOf("model_quantized.onnx"),
            LocalFileNameDisambiguator.orphanedLegacyFilenames(catalog),
        )
    }

    @Test
    fun `orphanedLegacyFilenames is empty when filename is still used locally`() {
        val catalog =
            listOf(
                CatalogFileIdentity(
                    fileName = "tokenizer.json",
                    sha256 = "tokenizer-sha",
                    huggingFacePath = "chat_title_generator",
                    utilityType = UtilityType.TITLE_GENERATOR_TOKENIZER,
                ),
            )

        assertTrue(LocalFileNameDisambiguator.orphanedLegacyFilenames(catalog).isEmpty())
    }
}