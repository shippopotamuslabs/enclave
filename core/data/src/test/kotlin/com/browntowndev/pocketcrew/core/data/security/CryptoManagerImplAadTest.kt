package com.browntowndev.pocketcrew.core.data.security

import com.browntowndev.pocketcrew.core.data.security.CryptoManagerImpl
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Arrays

class CryptoManagerImplAadTest {
    private lateinit var crypto: CryptoManagerImpl

    @BeforeEach
    fun setUp() {
        crypto = CryptoManagerImpl()
        val dek = crypto.generateDek()
        crypto.cacheDek(dek).getOrThrow()
    }

    // T1: round-trip with matching AAD succeeds
    @Test
    fun encrypt_decryptWithMatchingAad_returnsOriginalPlaintext() {
        val plaintext = "secret"
        val aadA = "row|field|1".toByteArray(Charsets.UTF_8)

        val ciphertext = crypto.encrypt(plaintext, aadA).getOrThrow()
        val decrypted = crypto.decrypt(ciphertext, aadA).getOrThrow()

        assertEquals(plaintext, decrypted)
    }

    // T2: decrypt with wrong AAD fails the Result
    @Test
    fun encrypt_decryptWithWrongAad_returnsFailure() {
        val plaintext = "same"
        val aadA = "row|field|1".toByteArray(Charsets.UTF_8)
        val aadB = "row|field|2".toByteArray(Charsets.UTF_8)

        val ciphertext = crypto.encrypt(plaintext, aadA).getOrThrow()
        val result = crypto.decrypt(ciphertext, aadB)

        assertTrue(result.isFailure)
    }

    // M1: Distinct ciphertexts for same plaintext when only entityId in AAD changes
    @Test
    fun encrypt_sameTextDifferentAad_producesDifferentCiphertexts() {
        val plaintext = "payload"
        val aad1 = "e|f|1".toByteArray(Charsets.UTF_8)
        val aad2 = "e|f|2".toByteArray(Charsets.UTF_8)

        val c1 = crypto.encrypt(plaintext, aad1).getOrThrow()
        val c2 = crypto.encrypt(plaintext, aad2).getOrThrow()

        assertFalse(Arrays.equals(c1, c2))
    }
}
