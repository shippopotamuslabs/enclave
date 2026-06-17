package com.browntowndev.enclave.domain.usecase.inference

import com.browntowndev.enclave.domain.port.repository.ModerationReportRepositoryPort
import javax.inject.Inject

class SubmitModerationReportUseCase
    @Inject
    constructor(
        private val moderationReportRepository: ModerationReportRepositoryPort,
    ) {
        suspend operator fun invoke(
            prompt: String,
            outcome: String,
        ): Result<Unit> = moderationReportRepository.submitReport(prompt, outcome)
    }
