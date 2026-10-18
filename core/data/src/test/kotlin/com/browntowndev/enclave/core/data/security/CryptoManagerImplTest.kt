package com.browntowndev.enclave.core.data.security

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * Runs under Robolectric so [android.util.Base64] resolves to the real
 * shadow implementation instead of a static mock bridging to java.util.Base64.
 * The real [CryptoManagerImpl] uses no Android Keystore — it is pure JCE —
 * so no keystore shadow is required.
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class CryptoManagerImplTest {
    private lateinit var cryptoManager: CryptoManagerImpl

    // Use a fixed salt for deterministic tests, but random for real usage
    private val testSalt = "test-salt-1234567890".toByteArray(Charsets.UTF_8)
    private val testPassword = "my-super-secret-vault-password"

    @BeforeEach
    fun setup() {
        cryptoManager = CryptoManagerImpl()
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
