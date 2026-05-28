package com.browntowndev.enclave.domain.port.telemetry

import com.browntowndev.enclave.domain.model.telemetry.ExceptionRecord
import com.browntowndev.enclave.domain.model.telemetry.InferenceOutcome
import com.browntowndev.enclave.domain.model.telemetry.ProviderMetric
import com.browntowndev.enclave.domain.model.telemetry.ToolOutcome

interface TelemetryEventPort {
    suspend fun emitInferenceOutcome(outcome: InferenceOutcome)

    suspend fun emitToolOutcome(outcome: ToolOutcome)

    suspend fun emitException(record: ExceptionRecord)

    suspend fun emitProviderMetric(metric: ProviderMetric)
}
