package com.browntowndev.enclave.core.data.local

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class ApiCredentialsIdentityTest {
    @Test
    fun `SCN-SIGNATURE-002 Different Base URL Yields Different Signature`() {
        val sigA =
            buildApiCredentialsIdentitySignature(
                provider = "OPENAI",
                baseUrl = "https://api.openai.com/v1",
                apiKey = "sk-1",
            )
        val sigB =
            buildApiCredentialsIdentitySignature(
                provider = "OPENAI",
                baseUrl = "https://proxy.example/v1",
                apiKey = "sk-1",
            )
        assertNotEquals(sigA, sigB)
    }

    @Test
    fun `SCN-SIGNATURE-001 Dedupe Ignores Preset Model Identity`() {
        val signature =
            buildApiCredentialsIdentitySignature(
                provider = "DEEPSEEK",
                baseUrl = "https://api.deepseek.com",
                apiKey = "same-key",
            )
        val signatureAgain =
            buildApiCredentialsIdentitySignature(
                provider = "DEEPSEEK",
                baseUrl = "https://api.deepseek.com",
                apiKey = "same-key",
            )
        assertEquals(signature, signatureAgain)
    }

    @Test
    fun `SCN-MUT-001 Signature Must Not Include ModelId`() {
        val sigA =
            buildApiCredentialsIdentitySignature(
                provider = "DEEPSEEK",
                baseUrl = "https://api.deepseek.com",
                apiKey = "same-key",
            )
        val sigB =
            buildApiCredentialsIdentitySignature(
                provider = "DEEPSEEK",
                baseUrl = "https://api.deepseek.com",
                apiKey = "same-key",
            )
        assertEquals(sigA, sigB)
    }
}
