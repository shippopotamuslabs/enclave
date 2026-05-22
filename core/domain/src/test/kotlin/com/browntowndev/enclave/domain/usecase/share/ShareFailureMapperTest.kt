package com.browntowndev.enclave.domain.usecase.share

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.UnknownHostException

class ShareFailureMapperTest {
    @Test
    fun `maps technical server validation errors to a generic share message`() {
        val message =
            ShareFailureMapper.userMessageFor(
                Exception("encryptedPayload and iv must be provided."),
            )

        assertEquals(ShareFailureMapper.DEFAULT_SHARE_MESSAGE, message)
    }

    @Test
    fun `maps unauthenticated errors to sign in message`() {
        val message =
            ShareFailureMapper.userMessageFor(
                Exception("User must be logged in to share chats."),
            )

        assertEquals(ShareFailureMapper.SIGN_IN_REQUIRED_MESSAGE, message)
    }

    @Test
    fun `maps network failures to network message`() {
        val message = ShareFailureMapper.userMessageFor(UnknownHostException("offline"))

        assertEquals(ShareFailureMapper.NETWORK_MESSAGE, message)
    }

    @Test
    fun `maps missing assistant message to friendly message`() {
        val message =
            ShareFailureMapper.userMessageFor(
                IllegalArgumentException("Assistant message not found"),
            )

        assertEquals(ShareFailureMapper.MESSAGE_NOT_FOUND_MESSAGE, message)
    }

    @Test
    fun `maps wrapped io failures to network message`() {
        val message =
            ShareFailureMapper.userMessageFor(
                RuntimeException("Callable failed", IOException("timeout")),
            )

        assertEquals(ShareFailureMapper.NETWORK_MESSAGE, message)
    }
}
