package com.browntowndev.enclave.domain.telemetry

import com.browntowndev.enclave.domain.model.telemetry.ExceptionRecord
import com.browntowndev.enclave.domain.model.telemetry.InferenceOutcome
import com.browntowndev.enclave.domain.model.telemetry.ProviderMetric
import com.browntowndev.enclave.domain.model.telemetry.TelemetrySeverity
import com.browntowndev.enclave.domain.model.telemetry.ToolOutcome
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.testing.FakeLoggingPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.jvm.javaMethod

/**
 * TELEMETRY-001 TDD Red — domain layer scenarios TEL-001..TEL-008, TEL-049, TEL-057.
 */
class Telemetry001DomainTddRedTest {
    @Test
    fun `TEL-001 TelemetrySeverity enum has DEBUG value`() {
        assertEquals("DEBUG", TelemetrySeverity.DEBUG.name)
    }

    @Test
    fun `TEL-002 TelemetrySeverity enum has INFO value`() {
        assertEquals("INFO", TelemetrySeverity.INFO.name)
    }

    @Test
    fun `TEL-003 TelemetrySeverity enum has WARNING value`() {
        assertEquals("WARNING", TelemetrySeverity.WARNING.name)
    }

    @Test
    fun `TEL-004 TelemetrySeverity enum has ERROR value`() {
        assertEquals("ERROR", TelemetrySeverity.ERROR.name)
    }

    @Test
    fun `TEL-005 InferenceOutcome data class creation with all fields`() {
        val outcome =
            InferenceOutcome(
                eventId = "evt-1",
                timestampMs = 1_717_000_000_000L,
                provider = "OpenAI",
                modelId = "gpt-4.1",
                latencyMs = 1500,
                tokenCount = 500,
                costUsd = BigDecimal("0.0025"),
                success = true,
                errorMessage = null,
                severity = TelemetrySeverity.INFO,
            )
        assertEquals("evt-1", outcome.eventId)
        assertEquals("OpenAI", outcome.provider)
        assertEquals(500, outcome.tokenCount)
    }

    @Test
    fun `TEL-006 ToolOutcome data class creation with all fields`() {
        val outcome =
            ToolOutcome(
                eventId = "evt-2",
                timestampMs = 1_717_000_000_000L,
                toolName = "search",
                latencyMs = 3000,
                success = true,
                errorMessage = null,
                severity = TelemetrySeverity.INFO,
                provider = "Anthropic",
                modelId = "claude-3-5-sonnet",
            )
        assertEquals("search", outcome.toolName)
        assertEquals("Anthropic", outcome.provider)
        assertEquals("claude-3-5-sonnet", outcome.modelId)
    }

    @Test
    fun `TEL-007 ExceptionRecord data class creation with all fields`() {
        val record =
            ExceptionRecord(
                eventId = "evt-3",
                timestampMs = 1_717_000_000_000L,
                tag = "InferenceService",
                message = "Connection timeout",
                throwableType = "java.io.IOException",
                stackTrace = "at com.example.InferenceService.run(InferenceService.kt:42)",
                severity = TelemetrySeverity.ERROR,
            )
        assertEquals("java.io.IOException", record.throwableType)
    }

    @Test
    fun `TEL-008 ProviderMetric data class creation with all fields`() {
        val metric =
            ProviderMetric(
                eventId = "evt-4",
                timestampMs = 1_717_000_000_000L,
                provider = "Anthropic",
                metricName = "tttf_ms",
                metricValue = 450.0,
                severity = TelemetrySeverity.INFO,
            )
        assertEquals(450.0, metric.metricValue)
    }

    @Test
    fun `TEL-049 LoggingPort interface no longer declares recordException method`() {
        val methodNames =
            LoggingPort::class.declaredFunctions
                .mapNotNull { it.javaMethod?.name }
                .toSet()
        assertEquals(setOf("debug", "info", "warning", "error"), methodNames)
        assertFalse(methodNames.contains("recordException"))
    }

    @Test
    fun `TEL-057 Test fakes no longer override recordException`() {
        val fakeClasses = listOf(FakeLoggingPort::class)
        fakeClasses.forEach { fakeClass ->
            val methodNames =
                fakeClass.declaredFunctions
                    .mapNotNull { it.javaMethod?.name }
                    .toSet()
            assertFalse(
                methodNames.contains("recordException"),
                "${fakeClass.simpleName} must not declare recordException",
            )
        }
    }
}
