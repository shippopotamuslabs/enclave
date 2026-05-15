package com.browntowndev.pocketcrew.core.data.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.crypto.Cipher

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidBiometricManagerTest {
    private lateinit var context: Context
    private lateinit var loggingPort: LoggingPort
    private lateinit var subject: AndroidBiometricManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("biometric_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        loggingPort = mockk(relaxed = true)
        subject = AndroidBiometricManager(context, loggingPort)
    }

    @Test
    fun hasSecureValue_falseUntilDataKeyWritten() {
        assertFalse(subject.hasSecureValue("my_key"))
        context.getSharedPreferences("biometric_prefs", Context.MODE_PRIVATE).edit().putString("my_key_data", "x").commit()
        assertTrue(subject.hasSecureValue("my_key"))
    }

    @Test
    fun clearSecureValue_removesDataAndIv() {
        val prefs = context.getSharedPreferences("biometric_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("k_data", "enc").putString("k_iv", "iv").commit()
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
    fun storeSecureBytesWithCipher_logsDoNotContainPlaintextSecret() =
        runTest {
            val secret = "super-secret-payload-987654"
            val cipher = mockk<Cipher>(relaxed = true)
            every { cipher.doFinal(any()) } returns byteArrayOf(1, 2, 3)
            every { cipher.iv } returns ByteArray(12) { 7 }

            subject.storeSecureBytesWithCipher("test_store_key", secret.toByteArray(Charsets.UTF_8), cipher)

            verify { loggingPort.debug(any(), match { !it.contains(secret) }) }
            verify { loggingPort.info(any(), match { !it.contains(secret) }) }
        }
}
