package com.browntowndev.enclave.domain.usecase.onboarding

import com.browntowndev.enclave.domain.model.onboarding.OnboardingFlowMode
import com.browntowndev.enclave.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.enclave.domain.port.repository.ApiModelRepositoryPort
import javax.inject.Inject

/**
 * Detects whether onboarding should show the full wizard or a shortened path for
 * users who already have model configuration from cloud sync.
 */
interface ResolveOnboardingFlowModeUseCase {
    suspend operator fun invoke(): OnboardingFlowMode
}

class ResolveOnboardingFlowModeUseCaseImpl
    @Inject
    constructor(
        private val apiModelRepository: ApiModelRepositoryPort,
        private val activeModelProvider: ActiveModelProviderPort,
    ) : ResolveOnboardingFlowModeUseCase {
        override suspend fun invoke(): OnboardingFlowMode {
            if (apiModelRepository.getAllCredentials().isNotEmpty()) {
                return OnboardingFlowMode.RESTORED
            }
            val hasSyncedSlotConfiguration =
                BulkAssignApiProviderUseCase.TEXT_SLOTS.any { slot ->
                    activeModelProvider.getActiveConfiguration(slot) != null
                }
            return if (hasSyncedSlotConfiguration) {
                OnboardingFlowMode.RESTORED
            } else {
                OnboardingFlowMode.FULL
            }
        }
    }
