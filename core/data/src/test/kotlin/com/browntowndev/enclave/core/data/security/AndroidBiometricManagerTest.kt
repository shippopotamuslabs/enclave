package com.browntowndev.enclave.core.data.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class AndroidBiometricManagerTest {
    private lateinit var context: Context
    private lateinit var loggingPort: FakeLoggingPort
    private lateinit var subject: AndroidBiometricManager

    @BeforeEach
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context
            .getSharedPreferences("biometric_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        loggingPort = FakeLoggingPort()
        subject = AndroidBiometricManager(context, loggingPort)
    }

    @Test
    fun hasSecureValue_falseUntilDataKeyWritten() {
        assertFalse(subject.hasSecureValue("my_key"))
        context
            .getSharedPreferences("biometric_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("my_key_data", "x")
            .commit()
        assertTrue(subject.hasSecureValue("my_key"))
    }

    @Test
    fun clearSecureValue_removesDataAndIv() {
        val prefs = context.getSharedPreferences("biometric_prefs", Context.MODE_PRIVATE)
        prefs
            .edit()
            .putString("k_data", "enc")
            .putString("k_iv", "iv")
            .commit()
        assertTrue(subject.hasSecureValue("k"))
        subject.clearSecureValue("k")
        assertFalse(subject.hasSecureValue("k"))
        assertNull(prefs.getString("k_data", null))
        assertNull(prefs.getString("k_iv", null))
    }

    @Test
    fun isBiometricAvailable_doesNotThrow() {
        subject.isBiometricAvailable()
    }

    @Test
    fun storeSecureBytesWithCipher_logsDoNotContainPlaintextSecret(): Unit =
        runTest {
            val secret = "super-secret-payload-987654"
            // Real AES-GCM cipher (Robolectric runs the JVM JCE providers):
            // doFinal encrypts for real and iv is a real generated 12-byte IV.
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(ByteArray(16) { 3 }, "AES"))

            val result = subject.storeSecureBytesWithCipher("test_store_key", secret.toByteArray(Charsets.UTF_8), cipher)

            assertTrue(result.isSuccess)
            // The encrypted payload and IV were persisted under the key.
            val prefs = context.getSharedPreferences("biometric_prefs", Context.MODE_PRIVATE)
            assertNotNull(prefs.getString("test_store_key_data", null))
            assertNotNull(prefs.getString("test_store_key_iv", null))
            // No recorded log message at any level contains the plaintext secret.
            assertTrue(loggingPort.debugCalls.isNotEmpty())
            assertTrue(loggingPort.debugCalls.all { (_, message) -> !message.contains(secret) })
            assertTrue(loggingPort.infoCalls.isNotEmpty())
            assertTrue(loggingPort.infoCalls.all { (_, message) -> !message.contains(secret) })
        }
}
