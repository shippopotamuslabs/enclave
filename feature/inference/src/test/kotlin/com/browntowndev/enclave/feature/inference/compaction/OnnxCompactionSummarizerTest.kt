package com.browntowndev.enclave.feature.inference.compaction

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.enclave.domain.model.config.UtilityType
import com.browntowndev.enclave.feature.inference.FakeUtilityModelFilePort
import com.browntowndev.enclave.feature.inference.utilityonnx.UtilityOnnxLoadCoordinator
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class OnnxCompactionSummarizerTest {
    private lateinit var context: Context
    private lateinit var utilityModelFilePort: FakeUtilityModelFilePort
    private lateinit var summarizer: OnnxCompactionSummarizer

    @BeforeEach
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        utilityModelFilePort = FakeUtilityModelFilePort()
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
            utilityModelFilePort.utilityPaths[UtilityType.TITLE_GENERATOR_ENCODER] = null
            utilityModelFilePort.utilityPaths[UtilityType.TITLE_GENERATOR_DECODER] = "decoder"
            utilityModelFilePort.utilityPaths[UtilityType.TITLE_GENERATOR_TOKENIZER] = "tokenizer"

            assertNull(summarizer.summarize("hello"))
        }

    @Test
    fun summarize_returnsNullWhenModelPathsMissing() =
        runTest {
            utilityModelFilePort.utilityPaths[UtilityType.TITLE_GENERATOR_ENCODER] = null
            utilityModelFilePort.utilityPaths[UtilityType.TITLE_GENERATOR_DECODER] = null
            utilityModelFilePort.utilityPaths[UtilityType.TITLE_GENERATOR_TOKENIZER] = null

            assertNull(summarizer.summarize("Summarize this history"))
        }

    @Test
    fun summarize_returnsNullForBlankPrompt() =
        runTest {
            utilityModelFilePort.utilityPaths[UtilityType.TITLE_GENERATOR_ENCODER] = "path"
            utilityModelFilePort.utilityPaths[UtilityType.TITLE_GENERATOR_DECODER] = "path"
            utilityModelFilePort.utilityPaths[UtilityType.TITLE_GENERATOR_TOKENIZER] = "path"

            assertNull(summarizer.summarize("   "))
        }
}
