package com.browntowndev.enclave.core.agent

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class RustToolEnvelopeParserTest {
    @Test
    fun parse_whenRustParserFails_wrapsUnavailableError() {
        val cause = LinkageError("missing JNI symbol")
        val parser = RustToolEnvelopeParser()
        parser.parseToolCallsDelegate = { _, _, _ -> throw cause }

        val error =
            assertFailsWith<IllegalStateException> {
                parser.parse("<tool_call>{}</tool_call>", "{}", "[]")
            }

        assertEquals("Rust tool parser unavailable", error.message)
        assertSame(cause, error.cause)
    }
}
