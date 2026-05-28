package com.browntowndev.enclave.domain.util.telemetry

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TelemetryPiiScrubberTest {
    @Test
    fun `scrub redacts email addresses`() {
        val result = TelemetryPiiScrubber.scrub("Contact user@example.com for help")
        assertEquals("Contact ${TelemetryPiiScrubber.REDACTED_EMAIL} for help", result)
    }

    @Test
    fun `scrub redacts spaced credit card numbers`() {
        val result = TelemetryPiiScrubber.scrub("Payment failed for card 4111 1111 1111 1111")
        assertEquals("Payment failed for card ${TelemetryPiiScrubber.REDACTED_CARD}", result)
    }

    @Test
    fun `scrub redacts dashed credit card numbers`() {
        val result = TelemetryPiiScrubber.scrub("Invalid card 3782-822463-10005")
        assertEquals("Invalid card ${TelemetryPiiScrubber.REDACTED_CARD}", result)
    }

    @Test
    fun `scrub redacts unix paths`() {
        val result = TelemetryPiiScrubber.scrub("Missing file /home/user/secret.txt")
        assertEquals("Missing file ${TelemetryPiiScrubber.REDACTED_PATH}", result)
    }

    @Test
    fun `scrub leaves null and blank unchanged`() {
        assertNull(TelemetryPiiScrubber.scrub(null))
        assertEquals("", TelemetryPiiScrubber.scrub(""))
    }

    @Test
    fun `scrub applies all redactions in one pass`() {
        val input = "user@example.com paid with 4111-1111-1111-1111 at /home/user/wallet.txt"
        val result = TelemetryPiiScrubber.scrub(input).orEmpty()
        assertFalse(result.contains("user@example.com"))
        assertFalse(result.contains("4111-1111-1111-1111"))
        assertFalse(result.contains("/home/user/wallet.txt"))
    }
}
