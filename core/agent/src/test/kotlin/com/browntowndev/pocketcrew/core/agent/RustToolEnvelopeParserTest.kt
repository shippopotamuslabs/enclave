package com.browntowndev.pocketcrew.core.agent

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import org.junit.jupiter.api.Test

class RustToolEnvelopeParserTest {
    @Test
    fun parse_whenRustParserFails_wrapsUnavailableError() {
        val cause = LinkageError("missing JNI symbol")
        val parser = RustToolEnvelopeParser()
        parser.parseToolCallsDelegate = { throw cause }

        val error = assertFailsWith<IllegalStateException> {
            parser.parse("<tool_call>{}</tool_call>")
        }

        assertEquals("Rust tool parser unavailable", error.message)
        assertSame(cause, error.cause)
    }
}
