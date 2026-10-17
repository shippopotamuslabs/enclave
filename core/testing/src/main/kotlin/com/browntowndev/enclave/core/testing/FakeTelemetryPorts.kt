package com.browntowndev.enclave.core.testing

import com.browntowndev.enclave.domain.model.telemetry.ExceptionRecord
import com.browntowndev.enclave.domain.model.telemetry.InferenceOutcome
import com.browntowndev.enclave.domain.model.telemetry.ProviderMetric
import com.browntowndev.enclave.domain.model.telemetry.ToolOutcome
import com.browntowndev.enclave.domain.port.telemetry.AutomaticErrorTrackingGate
import com.browntowndev.enclave.domain.port.telemetry.TelemetryEventPort

/** Recording [TelemetryEventPort] fake (ENC-28). */
class FakeTelemetryEventPort : TelemetryEventPort {
    val emittedExceptions = mutableListOf<ExceptionRecord>()
    val emittedInferenceOutcomes = mutableListOf<InferenceOutcome>()
    val emittedToolOutcomes = mutableListOf<ToolOutcome>()
    val emittedProviderMetrics = mutableListOf<ProviderMetric>()

    override suspend fun emitInferenceOutcome(outcome: InferenceOutcome) {
        emittedInferenceOutcomes += outcome
    }

    override suspend fun emitToolOutcome(outcome: ToolOutcome) {
        emittedToolOutcomes += outcome
    }

    override suspend fun emitException(record: ExceptionRecord) {
        emittedExceptions += record
    }

    override suspend fun emitProviderMetric(metric: ProviderMetric) {
        emittedProviderMetrics += metric
    }
}

/** Toggleable [AutomaticErrorTrackingGate] fake (ENC-28). */
class FakeAutomaticErrorTrackingGate(
    var enabled: Boolean = true,
) : AutomaticErrorTrackingGate {
    override fun isAutomaticErrorTrackingEnabled(): Boolean = enabled
}
