package com.browntowndev.enclave.feature.inference.moderation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.enclave.domain.port.repository.UtilityModelFilePort
import com.browntowndev.enclave.feature.inference.FakeUtilityModelFilePort
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(manifest = Config.NONE, sdk = [34])
class ResponseModerationClassifierTest {
    private lateinit var context: Context
    private lateinit var utilityModelFilePort: UtilityModelFilePort
    private lateinit var classifier: ResponseModerationClassifier

    @BeforeEach
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        // No path seeded: the fake resolves null, so the classifier degrades silently.
        utilityModelFilePort = FakeUtilityModelFilePort()
        classifier = ResponseModerationClassifier(context, utilityModelFilePort)
    }

    @Test
    fun classify_modelNotDownloaded_returnsEmptyScores() =
        runTest {
            val result = classifier.classify("some text that should be moderated")

            assertNotNull(result)
            assertTrue(result.scoresByLabel.isEmpty(), "Empty scores expected when model is unavailable.")
            assertTrue(result.thresholds.isEmpty())
        }

    @Test
    fun classify_emptyPrompt_returnsEmptyScores() =
        runTest {
            val result = classifier.classify("")

            assertNotNull(result)
            assertTrue(result.scoresByLabel.isEmpty())
        }

    @Test
    fun classify_calledRepeatedly_doesNotThrowAcrossInvocations() =
        runTest {
            repeat(3) {
                val result = classifier.classify("repeat test")
                assertTrue(result.scoresByLabel.isEmpty())
            }
        }
}
