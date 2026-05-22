package com.browntowndev.enclave.domain.usecase.inference

import com.browntowndev.enclave.domain.model.inference.XSearchParams
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BuildXQueryUseCaseTest {
    private val useCase = BuildXQueryUseCase()

    @Test
    fun `invoke with keywords only`() {
        val params = XSearchParams(keywords = listOf("Google", "I/O"))
        val result = useCase(params)
        assertEquals("Google I/O lang:en", result)
    }

    @Test
    fun `invoke with OR and NOT operators`() {
        val params =
            XSearchParams(
                keywords = listOf("AI"),
                any_keywords = listOf("artificial intelligence", "machine learning"),
                exclude_keywords = listOf("spam", "ads"),
            )
        val result = useCase(params)
        assertEquals("AI (artificial intelligence OR machine learning) -spam -ads lang:en", result)
    }

    @Test
    fun `invoke with complex multi-author OR query`() {
        // This verifies that we can build the exact structure requested:
        // (from:TwitterDev OR from:TwitterAPI) has:media
        // Note: -is:retweet and -is:nullcast are added automatically by the repository layer
        val params =
            XSearchParams(
                any_keywords = listOf("from:TwitterDev", "from:TwitterAPI"),
                has_media = true,
                language = null,
            )
        val result = useCase(params)
        assertEquals("(from:TwitterDev OR from:TwitterAPI) has:media", result)
    }

    @Test
    fun `invoke with natural disaster tracking example`() {
        // has:geo (from:NWSNHC OR from:NHC_Atlantic OR ...) -is:retweet
        val params =
            XSearchParams(
                has_geo = true,
                any_keywords = listOf("from:NWSNHC", "from:NHC_Atlantic", "from:NWSHouston"),
                language = null,
            )
        val result = useCase(params)
        assertEquals("(from:NWSNHC OR from:NHC_Atlantic OR from:NWSHouston) has:geo", result)
    }

    @Test
    fun `invoke with sentiment analysis example`() {
        // #nowplaying (happy OR exciting OR ...) (place_country:US OR place_country:MX OR ...) -horrible ...
        val params =
            XSearchParams(
                hashtags = listOf("nowplaying"),
                any_keywords = listOf("happy", "exciting", "favorite"),
                place_country = "US", // Single country for now
                exclude_keywords = listOf("horrible", "worst"),
                language = null,
            )
        val result = useCase(params)
        assertEquals("#nowplaying (happy OR exciting OR favorite) -horrible -worst place_country:US", result)
    }

    @Test
    fun `invoke fails when only conjunction-required operators are present`() {
        val params =
            XSearchParams(
                has_media = true,
                language = null,
            )
        org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
            useCase(params)
        }
    }

    @Test
    fun `invoke with phrases`() {
        val params = XSearchParams(phrases = listOf("artificial intelligence", "machine learning"))
        val result = useCase(params)
        assertEquals("\"artificial intelligence\" \"machine learning\" lang:en", result)
    }

    @Test
    fun `invoke with hashtags and mentions`() {
        val params =
            XSearchParams(
                hashtags = listOf("#AI", "tech"),
                mentions = listOf("@Google", "OpenAI"),
            )
        val result = useCase(params)
        assertEquals("#AI #tech @Google @OpenAI lang:en", result)
    }

    @Test
    fun `invoke with user filters`() {
        val params =
            XSearchParams(
                from_user = "@elonmusk",
                to_user = "XDevelopers",
            )
        val result = useCase(params)
        assertEquals("from:elonmusk to:XDevelopers lang:en", result)
    }

    @Test
    fun `invoke with content filters`() {
        val params =
            XSearchParams(
                keywords = listOf("cat"),
                has_images = true,
                has_links = true,
            )
        val result = useCase(params)
        assertEquals("cat has:images has:links lang:en", result)
    }

    @Test
    fun `invoke with custom language`() {
        val params =
            XSearchParams(
                keywords = listOf("noticias"),
                language = "es",
            )
        val result = useCase(params)
        assertEquals("noticias lang:es", result)
    }

    @Test
    fun `invoke with legacy query and new fields`() {
        val params =
            XSearchParams(
                query = "breaking news",
                keywords = listOf("AI"),
                has_videos = true,
            )
        val result = useCase(params)
        assertEquals("breaking news AI has:videos lang:en", result)
    }

    @Test
    fun `invoke with empty language returns no lang tag`() {
        val params =
            XSearchParams(
                keywords = listOf("test"),
                language = null,
            )
        val result = useCase(params)
        assertEquals("test", result)
    }
}
