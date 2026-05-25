package com.browntowndev.enclave.domain.usecase.telemetry

import com.browntowndev.enclave.domain.model.telemetry.UsagePeriod
import com.browntowndev.enclave.domain.model.telemetry.UsageSpendSummary
import com.browntowndev.enclave.domain.port.repository.InferenceTelemetryRepositoryPort
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveUsageSpendSummaryUseCase
    @Inject
    constructor(
        private val repository: InferenceTelemetryRepositoryPort,
    ) {
        operator fun invoke(period: UsagePeriod): Flow<UsageSpendSummary> =
            repository.observeUsageSpendSummary(period)
    }
