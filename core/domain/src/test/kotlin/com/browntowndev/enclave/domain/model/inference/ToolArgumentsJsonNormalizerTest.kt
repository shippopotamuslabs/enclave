package com.browntowndev.enclave.domain.model.inference

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class ToolArgumentsJsonNormalizerTest {
    @Test
    fun `coerces string false to boolean for append_blocks isComplete`() {
        val json = """{"artifactId":"art-1","blocks":[],"isComplete":"false"}"""
        val normalized =
            ToolArgumentsJsonNormalizer.normalize(
                ToolDefinition.APPEND_BLOCKS.schema,
                json,
            )

        val decoded: AppendBlocksParams = ToolDefinition.APPEND_BLOCKS.decodeArguments(normalized)
        assertFalse(decoded.isComplete)
    }

    @Test
    fun `coerces string true to boolean for append_blocks isComplete`() {
        val json = """{"artifactId":"art-1","blocks":[],"isComplete":"true"}"""
        val decoded: AppendBlocksParams = ToolDefinition.APPEND_BLOCKS.decodeArguments(json)

        assertEquals(true, decoded.isComplete)
    }

    @Test
    fun `x_user_tweets schema treats only username as required`() {
        assertEquals(listOf("username"), ToolDefinition.X_USER_TWEETS.schema.required)
    }

    @Test
    fun `x_user_tweets defaults max_results to 10`() {
        val decoded: XUserTweetsParams =
            ToolDefinition.X_USER_TWEETS.decodeArguments("""{"username":"elonmusk"}""")
        assertEquals(10, decoded.max_results)
    }

    @Test
    fun `drops blank optional next_token for x_user_tweets`() {
        val decoded: XUserTweetsParams =
            ToolDefinition.X_USER_TWEETS.decodeArguments("""{"username":"elonmusk","next_token":""}""")
        assertEquals(null, decoded.next_token)
    }

    @Test
    fun `coerces string booleans for x search filter flags`() {
        val json = """{"keywords":["android"],"has_geo":"true","has_media":"false"}"""
        val decoded: XSearchParams = ToolDefinition.X_SEARCH.decodeArguments(json)

        assertEquals(true, decoded.has_geo)
        assertFalse(decoded.has_media)
    }
}
