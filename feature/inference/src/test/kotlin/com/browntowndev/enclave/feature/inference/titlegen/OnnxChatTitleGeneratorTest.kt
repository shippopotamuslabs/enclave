package com.browntowndev.enclave.feature.inference.titlegen

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.enclave.domain.model.config.UtilityType
import com.browntowndev.enclave.domain.port.repository.UtilityModelFilePort
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(manifest = Config.NONE, sdk = [34])
class OnnxChatTitleGeneratorTest {
    private lateinit var context: Context
    private lateinit var utilityModelFilePort: UtilityModelFilePort
    private lateinit var titleGenerator: OnnxChatTitleGenerator

    @BeforeEach
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        utilityModelFilePort = mockk()
        titleGenerator = OnnxChatTitleGenerator(context, utilityModelFilePort)
    }

    @Test
    fun returnsNullOnEmptyPromptWithoutResolvingPaths() =
        runTest {
            val result = titleGenerator.generateTitle("   ")
            assertNull(result)
        }

    @Test
    fun returnsNullAndDegradesSilentlyWhenGraphPathIsUnresolved() =
        runTest {
            coEvery { utilityModelFilePort.resolveUtilityModelPath(UtilityType.TITLE_GENERATOR_ENCODER) } returns null
            coEvery { utilityModelFilePort.resolveUtilityModelPath(UtilityType.TITLE_GENERATOR_DECODER) } returns "path/to/data"
            coEvery { utilityModelFilePort.resolveUtilityModelPath(UtilityType.TITLE_GENERATOR_TOKENIZER) } returns "path/to/tokenizer"

            val result = titleGenerator.generateTitle("Generate title please")
            assertNull(result)
        }

    @Test
    fun returnsNullAndDegradesSilentlyWhenDataPathIsUnresolved() =
        runTest {
            coEvery { utilityModelFilePort.resolveUtilityModelPath(UtilityType.TITLE_GENERATOR_ENCODER) } returns "path/to/model.onnx"
            coEvery { utilityModelFilePort.resolveUtilityModelPath(UtilityType.TITLE_GENERATOR_DECODER) } returns null
            coEvery { utilityModelFilePort.resolveUtilityModelPath(UtilityType.TITLE_GENERATOR_TOKENIZER) } returns "path/to/tokenizer"

            val result = titleGenerator.generateTitle("Generate title please")
            assertNull(result)
        }

    @Test
    fun returnsNullAndDegradesSilentlyWhenTokenizerPathIsUnresolved() =
        runTest {
            coEvery { utilityModelFilePort.resolveUtilityModelPath(UtilityType.TITLE_GENERATOR_ENCODER) } returns "path/to/model.onnx"
            coEvery { utilityModelFilePort.resolveUtilityModelPath(UtilityType.TITLE_GENERATOR_DECODER) } returns "path/to/data"
            coEvery { utilityModelFilePort.resolveUtilityModelPath(UtilityType.TITLE_GENERATOR_TOKENIZER) } returns null

            val result = titleGenerator.generateTitle("Generate title please")
            assertNull(result)
        }

    @Test
    fun returnsNullAndDegradesSilentlyOnFileReadingExceptions() =
        runTest {
            coEvery { utilityModelFilePort.resolveUtilityModelPath(UtilityType.TITLE_GENERATOR_ENCODER) } returns "non_existent_file.onnx"
            coEvery { utilityModelFilePort.resolveUtilityModelPath(UtilityType.TITLE_GENERATOR_DECODER) } returns "non_existent_data"
            coEvery { utilityModelFilePort.resolveUtilityModelPath(UtilityType.TITLE_GENERATOR_TOKENIZER) } returns "non_existent_tokenizer.json"

            val result = titleGenerator.generateTitle("Generate title please")
            assertNull(result)
        }
}
