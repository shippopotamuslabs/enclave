package com.browntowndev.enclave.feature.inference.moderation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.enclave.domain.port.inference.ImageModerationResult
import com.browntowndev.enclave.feature.inference.FakeUtilityModelFilePort
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(manifest = Config.NONE, sdk = [34])
class OnnxImageSafetyClassifierTest {
    private lateinit var context: Context
    private lateinit var classifier: OnnxImageSafetyClassifier

    @BeforeEach
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        // No path seeded: the fake resolves null, so the classifier degrades silently.
        val utilityModelFilePort = FakeUtilityModelFilePort()
        classifier = OnnxImageSafetyClassifier(context, utilityModelFilePort)
    }

    @Test
    fun classify_malformedBytes_degradesSilentlyToNotBlocked() =
        runTest {
            val result = classifier.classify(byteArrayOf(0x00, 0x01, 0x02))

            assertNotBlocked(result)
        }

    @Test
    fun classify_emptyBytes_neverThrowsAndDegrades() =
        runTest {
            val result = classifier.classify(ByteArray(0))

            assertNotBlocked(result)
        }

    @Test
    fun classify_calledRepeatedly_doesNotThrowAcrossInvocations() =
        runTest {
            repeat(3) {
                val result = classifier.classify(byteArrayOf(0x00))
                assertNotBlocked(result)
            }
        }

    private fun assertNotBlocked(result: ImageModerationResult) {
        assertNotNull(result)
        assertFalse(result.blocked, "Silent-degrade path must never report a block.")
    }
}
