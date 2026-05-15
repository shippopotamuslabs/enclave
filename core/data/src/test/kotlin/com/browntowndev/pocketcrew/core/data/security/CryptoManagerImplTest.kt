package com.browntowndev.pocketcrew.core.data.security

import android.content.SharedPreferences
import android.util.Base64
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
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
            java.util.Base64
                .getEncoder()
                .encodeToString(firstArg() as ByteArray)
        }
        every { Base64.decode(any<String>(), any()) } answers {
            java.util.Base64
                .getDecoder()
                .decode(firstArg() as String)
        }

        mockPrefs = mockk()
        mockEditor = mockk()
        every { mockPrefs.edit() } returns mockEditor
        every { mockEditor.putString(any(), any()) } returns mockEditor
        every { mockEditor.remove(any()) } returns mockEditor
        every { mockEditor.apply() } returns Unit
        every { mockPrefs.getString(any(), any()) } returns null

        cryptoManager = CryptoManagerImpl()
    }

    @AfterEach
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `initially has no cached key`() {
        assertFalse(cryptoManager.hasCachedKey())
    }

    private fun unlockWithPassword() {
        val kek = cryptoManager.deriveKek(testPassword, testSalt).getOrThrow()
        val dek = cryptoManager.generateDek()
        val wrapped = cryptoManager.wrapDek(dek, kek).getOrThrow()
        val unwrapped = cryptoManager.unwrapDek(wrapped, kek).getOrThrow()
        cryptoManager.cacheDek(unwrapped).getOrThrow()
    }

    @Test
    fun `clearKey removes cached key`() {
        unlockWithPassword()
        assertTrue(cryptoManager.hasCachedKey())

        cryptoManager.clearKey()
        assertFalse(cryptoManager.hasCachedKey())
    }

    @Test
    fun `encrypt and decrypt cycle returns original plaintext`() {
        unlockWithPassword()

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
        unlockWithPassword()
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

    @Test
    fun `encryptWithKey and decryptWithKey cycle returns original plaintext`() {
        val key = cryptoManager.generateRandomKey(32)
        val iv = cryptoManager.generateRandomIv(12)
        val data = "test-data-for-cloud-generation".toByteArray()

        val encryptedResult = cryptoManager.encryptWithKey(data, key, iv)
        assertTrue(encryptedResult.isSuccess)

        val ciphertext = encryptedResult.getOrNull()!!

        val decryptedResult = cryptoManager.decryptWithKey(ciphertext, key, iv)
        assertTrue(decryptedResult.isSuccess)
        assertEquals("test-data-for-cloud-generation", String(decryptedResult.getOrNull()!!))
    }

    @Test
    fun `generateDek returns 32 bytes by default`() {
        val dek = cryptoManager.generateDek()
        assertEquals(32, dek.size)
    }

    @Test
    fun `generateDek produces different bytes on successive calls`() {
        val a = cryptoManager.generateDek()
        val b = cryptoManager.generateDek()
        assertFalse(a.contentEquals(b), "Two DEKs should not be identical")
    }

    @Test
    fun `wrapDek and unwrapDek round trip returns original DEK`() {
        val kek = cryptoManager.deriveKek(testPassword, testSalt).getOrThrow()
        val dek = cryptoManager.generateDek()

        val wrapped = cryptoManager.wrapDek(dek, kek).getOrThrow()
        val unwrapped = cryptoManager.unwrapDek(wrapped, kek).getOrThrow()

        assertArrayEquals(dek, unwrapped)
    }

    @Test
    fun `wrapDek prepends 12-byte IV before ciphertext`() {
        val kek = cryptoManager.deriveKek(testPassword, testSalt).getOrThrow()
        val dek = cryptoManager.generateDek()

        val wrapped = cryptoManager.wrapDek(dek, kek).getOrThrow()

        // 12 bytes IV + 32 bytes DEK + 16 bytes GCM tag = 60 bytes
        assertEquals(12 + 32 + 16, wrapped.size)
    }

    @Test
    fun `unwrapDek with wrong KEK fails`() {
        val correctKek = cryptoManager.deriveKek(testPassword, testSalt).getOrThrow()
        val wrongKek = cryptoManager.deriveKek("different-password", testSalt).getOrThrow()
        val dek = cryptoManager.generateDek()

        val wrapped = cryptoManager.wrapDek(dek, correctKek).getOrThrow()
        val result = cryptoManager.unwrapDek(wrapped, wrongKek)

        assertTrue(result.isFailure)
    }

    @Test
    fun `unwrapDek with corrupted blob fails`() {
        val kek = cryptoManager.deriveKek(testPassword, testSalt).getOrThrow()
        val dek = cryptoManager.generateDek()
        val wrapped = cryptoManager.wrapDek(dek, kek).getOrThrow()

        wrapped[wrapped.size - 1] = (wrapped[wrapped.size - 1] + 1).toByte()

        assertTrue(cryptoManager.unwrapDek(wrapped, kek).isFailure)
    }

    @Test
    fun `cacheDek caches DEK and unlocks vault`() {
        val dek = cryptoManager.generateDek()

        val result = cryptoManager.cacheDek(dek)

        assertTrue(result.isSuccess)
        assertTrue(cryptoManager.hasCachedKey())
        assertArrayEquals(dek, cryptoManager.getCachedDek())
    }

    @Test
    fun `cacheDek then encrypt and decrypt round trips`() {
        val dek = cryptoManager.generateDek()
        cryptoManager.cacheDek(dek)

        val encrypted = cryptoManager.encrypt("dek-cycle").getOrThrow()
        val decrypted = cryptoManager.decrypt(encrypted).getOrThrow()

        assertEquals("dek-cycle", decrypted)
    }

    @Test
    fun `getCachedDek returns null when locked`() {
        assertNull(cryptoManager.getCachedDek())
    }

    @Test
    fun `clearKey wipes cached DEK`() {
        cryptoManager.cacheDek(cryptoManager.generateDek())
        assertTrue(cryptoManager.hasCachedKey())

        cryptoManager.clearKey()

        assertFalse(cryptoManager.hasCachedKey())
        assertNull(cryptoManager.getCachedDek())
    }

    @Test
    fun `deriveKek is deterministic for same password and salt`() {
        val a = cryptoManager.deriveKek(testPassword, testSalt).getOrThrow()
        val b = cryptoManager.deriveKek(testPassword, testSalt).getOrThrow()

        assertArrayEquals(a, b)
        assertEquals(32, a.size)
    }

    @Test
    fun `deriveKek differs for different passwords`() {
        val a = cryptoManager.deriveKek("password-a", testSalt).getOrThrow()
        val b = cryptoManager.deriveKek("password-b", testSalt).getOrThrow()

        assertFalse(a.contentEquals(b))
    }

    @Test
    fun `deriveKek does not cache the derived key`() {
        cryptoManager.deriveKek(testPassword, testSalt).getOrThrow()

        assertFalse(cryptoManager.hasCachedKey())
        assertNotNull(cryptoManager.deriveKek(testPassword, testSalt).getOrNull())
    }
}
