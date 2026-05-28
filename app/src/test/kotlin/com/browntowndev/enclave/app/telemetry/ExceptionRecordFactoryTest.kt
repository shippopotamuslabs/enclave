package com.browntowndev.enclave.app.telemetry

import com.browntowndev.enclave.domain.model.telemetry.TelemetrySeverity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ExceptionRecordFactoryTest {
    @Test
    fun `create with detailed tracking enabled preserves diagnostic fields`() {
        val throwable = IllegalStateException("secret exception")
        val record =
            ExceptionRecordFactory.create(
                tag = "ChatRepo",
                message = "failed for user@example.com",
                throwable = throwable,
                detailedTrackingEnabled = true,
            )

        assertEquals("ChatRepo", record.tag)
        assertEquals("failed for user@example.com", record.message)
        assertEquals(throwable::class.java.name, record.throwableType)
        assertEquals(TelemetrySeverity.ERROR, record.severity)
        assert(record.stackTrace.contains("IllegalStateException"))
    }

    @Test
    fun `create with detailed tracking disabled redacts diagnostic fields but keeps throwable type`() {
        val throwable = IllegalStateException("secret exception")
        val record =
            ExceptionRecordFactory.create(
                tag = "ChatRepo",
                message = "failed for user@example.com",
                throwable = throwable,
                detailedTrackingEnabled = false,
            )

        assertEquals(ExceptionRecordFactory.REDACTED, record.tag)
        assertEquals(ExceptionRecordFactory.REDACTED, record.message)
        assertEquals(ExceptionRecordFactory.REDACTED, record.stackTrace)
        assertEquals(throwable::class.java.name, record.throwableType)
    }
}
