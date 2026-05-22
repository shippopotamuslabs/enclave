package com.browntowndev.enclave.domain.usecase.share

import java.io.IOException
import java.net.UnknownHostException

internal object ShareFailureMapper {
    const val DEFAULT_SHARE_MESSAGE = "Couldn't share this conversation. Please try again."
    const val SIGN_IN_REQUIRED_MESSAGE = "Sign in to share conversations."
    const val NETWORK_MESSAGE = "Check your internet connection and try again."
    const val MESSAGE_NOT_FOUND_MESSAGE = "That message couldn't be found. Try again from the latest reply."

    fun userMessageFor(throwable: Throwable): String {
        val errors = generateSequence(throwable) { it.cause }.toList()
        val messages = errors.mapNotNull { it.message?.lowercase() }

        if (errors.any { it is UnknownHostException || (it is IOException && it !is java.io.InterruptedIOException) }) {
            return NETWORK_MESSAGE
        }

        if (messages.any { it.contains("unauthenticated") || it.contains("must be logged in") }) {
            return SIGN_IN_REQUIRED_MESSAGE
        }

        if (messages.any { it.contains("assistant message not found") }) {
            return MESSAGE_NOT_FOUND_MESSAGE
        }

        return DEFAULT_SHARE_MESSAGE
    }
}
