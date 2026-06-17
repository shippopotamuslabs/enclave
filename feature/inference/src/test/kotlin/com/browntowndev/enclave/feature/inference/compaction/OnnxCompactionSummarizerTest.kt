package com.browntowndev.enclave.feature.inference.compaction

import android.content.Context
import com.browntowndev.enclave.domain.model.config.UtilityType
import com.browntowndev.enclave.domain.port.repository.UtilityModelFilePort
import com.browntowndev.enclave.feature.inference.utilityonnx.UtilityOnnxLoadCoordinator
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class OnnxCompactionSummarizerTest {
    private lateinit var context: Context
    private lateinit var utilityModelFilePort: UtilityModelFilePort
    private lateinit var summarizer: OnnxCompactionSummarizer

    @BeforeEach
    fun setUp() {
        context = mockk(relaxed = true)
        utilityModelFilePort = mockk()
        summarizer =
            OnnxCompactionSummarizer(
                context = context,
                utilityModelFilePort = utilityModelFilePort,
                loadCoordinator = UtilityOnnxLoadCoordinator(),
            )
    }

    @Test
    fun isModelInstalled_falseWhenEncoderMissing() =
        runTest {
            coEvery { utilityModelFilePort.resolveUtilityModelPath(UtilityType.TITLE_GENERATOR_ENCODER) } returns null
            coEvery { utilityModelFilePort.resolveUtilityModelPath(UtilityType.TITLE_GENERATOR_DECODER) } returns "decoder"
            coEvery { utilityModelFilePort.resolveUtilityModelPath(UtilityType.TITLE_GENERATOR_TOKENIZER) } returns "tokenizer"

            assertNull(summarizer.summarize("hello"))
        }

    @Test
    fun summarize_returnsNullWhenModelPathsMissing() =
        runTest {
            coEvery { utilityModelFilePort.resolveUtilityModelPath(UtilityType.TITLE_GENERATOR_ENCODER) } returns null
            coEvery { utilityModelFilePort.resolveUtilityModelPath(UtilityType.TITLE_GENERATOR_DECODER) } returns null
            coEvery { utilityModelFilePort.resolveUtilityModelPath(UtilityType.TITLE_GENERATOR_TOKENIZER) } returns null

            assertNull(summarizer.summarize("Summarize this history"))
        }

    @Test
    fun summarize_returnsNullForBlankPrompt() =
        runTest {
            coEvery { utilityModelFilePort.resolveUtilityModelPath(any()) } returns "path"
            every { context.assets } returns mockk(relaxed = true)

            assertNull(summarizer.summarize("   "))
        }
}