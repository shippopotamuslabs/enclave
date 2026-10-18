package com.browntowndev.enclave.feature.inference.titlegen

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
@Config(manifest = Config.NONE, sdk = [34])
class OnnxChatTitleGeneratorTest {
    private lateinit var context: Context
    private lateinit var utilityModelFilePort: FakeUtilityModelFilePort
    private lateinit var titleGenerator: OnnxChatTitleGenerator

    @BeforeEach
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        utilityModelFilePort = FakeUtilityModelFilePort()
        titleGenerator =
            OnnxChatTitleGenerator(
                context = context,
                utilityModelFilePort = utilityModelFilePort,
                loadCoordinator = UtilityOnnxLoadCoordinator(),
            )
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
            utilityModelFilePort.utilityPaths[UtilityType.TITLE_GENERATOR_ENCODER] = null
            utilityModelFilePort.utilityPaths[UtilityType.TITLE_GENERATOR_DECODER] = "path/to/data"
            utilityModelFilePort.utilityPaths[UtilityType.TITLE_GENERATOR_TOKENIZER] = "path/to/tokenizer"

            val result = titleGenerator.generateTitle("Generate title please")
            assertNull(result)
        }

    @Test
    fun returnsNullAndDegradesSilentlyWhenDataPathIsUnresolved() =
        runTest {
            utilityModelFilePort.utilityPaths[UtilityType.TITLE_GENERATOR_ENCODER] = "path/to/model.onnx"
            utilityModelFilePort.utilityPaths[UtilityType.TITLE_GENERATOR_DECODER] = null
            utilityModelFilePort.utilityPaths[UtilityType.TITLE_GENERATOR_TOKENIZER] = "path/to/tokenizer"

            val result = titleGenerator.generateTitle("Generate title please")
            assertNull(result)
        }

    @Test
    fun returnsNullAndDegradesSilentlyWhenTokenizerPathIsUnresolved() =
        runTest {
            utilityModelFilePort.utilityPaths[UtilityType.TITLE_GENERATOR_ENCODER] = "path/to/model.onnx"
            utilityModelFilePort.utilityPaths[UtilityType.TITLE_GENERATOR_DECODER] = "path/to/data"
            utilityModelFilePort.utilityPaths[UtilityType.TITLE_GENERATOR_TOKENIZER] = null

            val result = titleGenerator.generateTitle("Generate title please")
            assertNull(result)
        }

    @Test
    fun returnsNullAndDegradesSilentlyOnFileReadingExceptions() =
        runTest {
            utilityModelFilePort.utilityPaths[UtilityType.TITLE_GENERATOR_ENCODER] = "non_existent_file.onnx"
            utilityModelFilePort.utilityPaths[UtilityType.TITLE_GENERATOR_DECODER] = "non_existent_data"
            utilityModelFilePort.utilityPaths[UtilityType.TITLE_GENERATOR_TOKENIZER] = "non_existent_tokenizer.json"

            val result = titleGenerator.generateTitle("Generate title please")
            assertNull(result)
        }
}
