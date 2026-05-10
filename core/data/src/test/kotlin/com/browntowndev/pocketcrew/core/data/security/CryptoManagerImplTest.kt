package com.browntowndev.pocketcrew.core.data.security

import android.content.SharedPreferences
import android.util.Base64
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CryptoManagerImplTest {

    private lateinit var cryptoManager: CryptoManagerImpl
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor
    
    // Use a fixed salt for deterministic tests, but random for real usage
    private val testSalt = "test-salt-1234567890".toByteArray(Charsets.UTF_8)
    private val testPassword = "my-super-secret-vault-password"

    @BeforeEach
    fun setup() {
        mockkStatic(Base64::class)
        every { Base64.encodeToString(any(), any()) } answers { 
            java.util.Base64.getEncoder().encodeToString(firstArg() as ByteArray)
        }
        every { Base64.decode(any<String>(), any()) } answers { 
            java.util.Base64.getDecoder().decode(firstArg() as String)
        }

        mockPrefs = mockk()
        mockEditor = mockk()
        every { mockPrefs.edit() } returns mockEditor
        every { mockEditor.putString(any(), any()) } returns mockEditor
        every { mockEditor.remove(any()) } returns mockEditor
        every { mockEditor.apply() } returns Unit
        every { mockPrefs.getString(any(), any()) } returns null

        cryptoManager = CryptoManagerImpl { mockPrefs }
    }

    @AfterEach
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `initially has no cached key`() {
        assertFalse(cryptoManager.hasCachedKey())
    }

    @Test
    fun `deriveAndCacheKey successfully caches key`() {
        val result = cryptoManager.deriveAndCacheKey(testPassword, testSalt)
        assertTrue(result.isSuccess)
        assertTrue(cryptoManager.hasCachedKey())
    }

    @Test
    fun `clearKey removes cached key`() {
        cryptoManager.deriveAndCacheKey(testPassword, testSalt)
        assertTrue(cryptoManager.hasCachedKey())
        
        cryptoManager.clearKey()
        assertFalse(cryptoManager.hasCachedKey())
    }

    @Test
    fun `encrypt and decrypt cycle returns original plaintext`() {
        cryptoManager.deriveAndCacheKey(testPassword, testSalt)
        
        val plaintext = "my-secret-api-key-sk-12345"
        val encryptedResult = cryptoManager.encrypt(plaintext)
        assertTrue(encryptedResult.isSuccess)
        
        val ciphertext = encryptedResult.getOrNull()!!
        
        val decryptedResult = cryptoManager.decrypt(ciphertext)
        assertTrue(decryptedResult.isSuccess)
        assertEquals(plaintext, decryptedResult.getOrNull())
    }

    @Test
    fun `encrypt fails if no key is cached`() {
        val result = cryptoManager.encrypt("plaintext")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun `decrypt fails if no key is cached`() {
        val result = cryptoManager.decrypt(ByteArray(16))
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun `decrypt fails if ciphertext is corrupted`() {
        cryptoManager.deriveAndCacheKey(testPassword, testSalt)
        val encryptedResult = cryptoManager.encrypt("plaintext")
        val ciphertext = encryptedResult.getOrNull()!!
        
        // Corrupt the ciphertext by flipping a bit
        ciphertext[ciphertext.size - 1] = (ciphertext[ciphertext.size - 1] + 1).toByte()
        
        val decryptedResult = cryptoManager.decrypt(ciphertext)
        assertTrue(decryptedResult.isFailure)
    }

    @Test
    fun `generateRandomKey returns correct size`() {
        val key = cryptoManager.generateRandomKey(32)
        assertEquals(32, key.size)
    }

    @Test
    fun `generateRandomIv returns correct size`() {
        val iv = cryptoManager.generateRandomIv(12)
        assertEquals(12, iv.size)
    }

    @Test
    fun `encryptWithKey uses provided key and iv`() {
        val key = cryptoManager.generateRandomKey(32)
        val iv = cryptoManager.generateRandomIv(12)
        val data = "test-data".toByteArray()
        
        val result = cryptoManager.encryptWithKey(data, key, iv)
        assertTrue(result.isSuccess)
        
        // We can't easily decrypt without adding decryptWithKey to the port,
        // but we can verify it doesn't crash and returns data.
        assertTrue(result.getOrNull()!!.isNotEmpty())
    }
}
