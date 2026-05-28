package com.browntowndev.enclave.core.data.repository

import com.browntowndev.enclave.core.data.local.TelemetryEventDao
import com.browntowndev.enclave.core.data.mapper.telemetry.toEntity
import com.browntowndev.enclave.domain.model.telemetry.ExceptionRecord
import com.browntowndev.enclave.domain.model.telemetry.InferenceOutcome
import com.browntowndev.enclave.domain.model.telemetry.ProviderMetric
import com.browntowndev.enclave.domain.model.telemetry.ToolOutcome
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.telemetry.TelemetryEventPort
import com.browntowndev.enclave.domain.qualifier.InternalTelemetryLogging
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TelemetryEventRepositoryImpl
    @Inject
    constructor(
        private val dao: TelemetryEventDao,
        @InternalTelemetryLogging private val loggingPort: LoggingPort,
    ) : TelemetryEventPort {
        companion object {
            private const val TAG = "TelemetryEventRepository"
            private const val MAX_ROWS = 50_000
            private const val EVICTION_BATCH = 1_000
        }

        override suspend fun emitInferenceOutcome(outcome: InferenceOutcome) {
            persist(outcome.toEntity())
        }

        override suspend fun emitToolOutcome(outcome: ToolOutcome) {
            persist(outcome.toEntity())
        }

        override suspend fun emitException(record: ExceptionRecord) {
            persist(record.toEntity())
        }

        override suspend fun emitProviderMetric(metric: ProviderMetric) {
            persist(metric.toEntity())
        }

        private suspend fun persist(entity: com.browntowndev.enclave.core.data.local.TelemetryEventEntity) {
            runCatching {
                dao.insert(entity)
                val count = dao.getCount()
                if (count > MAX_ROWS) {
                    dao.evictOldest(EVICTION_BATCH)
                }
            }.onFailure { error ->
                loggingPort.warning(TAG, "Failed to persist telemetry event: ${error.message}")
            }
        }
    }
