package com.browntowndev.enclave.core.testing

import com.browntowndev.enclave.domain.port.inference.LoggingPort

/** Recording [LoggingPort] fake (ENC-28). Keeps every call for assertions. */
class FakeLoggingPort : LoggingPort {
    data class ErrorCall(
        val tag: String,
        val message: String,
        val throwable: Throwable?,
    )

    val debugCalls = mutableListOf<Pair<String, String>>()
    val infoCalls = mutableListOf<Pair<String, String>>()
    val warningCalls = mutableListOf<Pair<String, String>>()
    val errorCalls = mutableListOf<ErrorCall>()

    override fun debug(tag: String, message: String) {
        debugCalls += tag to message
    }

    override fun info(tag: String, message: String) {
        infoCalls += tag to message
    }

    override fun warning(tag: String, message: String) {
        warningCalls += tag to message
    }

    override fun error(tag: String, message: String, throwable: Throwable?) {
        errorCalls += ErrorCall(tag, message, throwable)
    }
}
