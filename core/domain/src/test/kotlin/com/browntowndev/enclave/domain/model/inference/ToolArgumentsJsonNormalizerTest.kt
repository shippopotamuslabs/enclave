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
    fun `drops blank optional next_token for x_user_tweets`() {
        val json = """{"username":"elonmusk","next_token":""}"""
        val normalized =
            ToolArgumentsJsonNormalizer.normalize(
                ToolDefinition.X_USER_TWEETS.schema,
                json,
            )

        val decoded: XUserTweetsParams = ToolDefinition.X_USER_TWEETS.decodeArguments(normalized)
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
