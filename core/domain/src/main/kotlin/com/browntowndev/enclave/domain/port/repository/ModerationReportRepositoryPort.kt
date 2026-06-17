package com.browntowndev.enclave.domain.port.repository

interface ModerationReportRepositoryPort {
    suspend fun submitReport(
        prompt: String,
        outcome: String,
    ): Result<Unit>
}
