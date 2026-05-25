package com.browntowndev.enclave.domain.usecase.inference

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DeriveXSearchParamsFromPromptUseCaseTest {

    private val useCase = DeriveXSearchParamsFromPromptUseCase()

    @Test
    fun `XPRE-04 Long conversational prompt without entities skips pre-retrieval`() {
        val prompt = "Hello assistant! I was wondering if you could help me compose a nice birthday invitation for my friend who turns 30 next week. They like hiking and pasta, so maybe something with that?"
        val result = useCase(prompt)

        assertNull(result.params)
        assertEquals("Conversational prompt too long without entities", result.skipReason)
    }

    @Test
    fun `usecase extracts entities and keywords`() {
        val prompt = "Find latest breaking news on #AI and OpenAI @sama"
        val result = useCase(prompt)

        val params = result.params
        assertNotNull(params)
        assertEquals(listOf("AI"), params?.hashtags)
        assertEquals(listOf("sama"), params?.mentions)
        // keywords includes "news" and "breaking" (standard stop words filtered out)
        val keywords = params?.keywords ?: emptyList()
        org.junit.jupiter.api.Assertions.assertTrue(keywords.contains("news"))
        org.junit.jupiter.api.Assertions.assertTrue(keywords.contains("breaking"))
        assertNull(result.skipReason)
    }

    @Test
    fun `usecase handles prompt with non-status X URL`() {
        val prompt = "Check out the profile https://x.com/OpenAI and search for AI news"
        val result = useCase(prompt)

        val params = result.params
        assertNotNull(params)
        val keywords = params?.keywords ?: emptyList()
        org.junit.jupiter.api.Assertions.assertTrue(keywords.contains("news"))
        // Check that non-status x.com/OpenAI doesn't trigger status check, just normal keywords
        assertNull(result.skipReason)
    }

    @Test
    fun `usecase extracts double-quoted phrases`() {
        val prompt = "search for \"artificial intelligence\" and \"machine learning\""
        val result = useCase(prompt)
        val params = result.params
        assertNotNull(params)
        assertEquals(listOf("artificial intelligence", "machine learning"), params?.phrases)
    }

    @Test
    fun `usecase limits keywords to at most 6`() {
        val prompt = "apple banana cherry date elderberry fig grape honeydew"
        val result = useCase(prompt)
        val params = result.params
        assertNotNull(params)
        val keywords = params?.keywords ?: emptyList()
        assertTrue(keywords.size <= 6)
    }

    @Test
    fun `usecase truncates keywords to keep composed query length under 128 characters`() {
        val prompt = "verylongkeywordone verylongkeywordtwo verylongkeywordthree verylongkeywordfour verylongkeywordfive verylongkeywordsix"
        val result = useCase(prompt)
        val params = result.params
        assertNotNull(params)
        val queryStr = com.browntowndev.enclave.domain.usecase.inference.BuildXQueryUseCase()(params!!)
        assertTrue(queryStr.length < 128)
    }

    @Test
    fun `usecase returns skip reason when no search terms are extracted`() {
        val prompt = "a about above again against"
        val result = useCase(prompt)
        assertNull(result.params)
        assertEquals("No search terms extracted", result.skipReason)
    }
}
