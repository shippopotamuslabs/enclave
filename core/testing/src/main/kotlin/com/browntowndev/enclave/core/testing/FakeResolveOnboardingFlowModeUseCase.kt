package com.browntowndev.enclave.core.testing

import com.browntowndev.enclave.domain.model.onboarding.OnboardingFlowMode
import com.browntowndev.enclave.domain.usecase.onboarding.ResolveOnboardingFlowModeUseCase

/** [ResolveOnboardingFlowModeUseCase] fake (ENC-28). Returns [mode] verbatim. */
class FakeResolveOnboardingFlowModeUseCase(
    var mode: OnboardingFlowMode = OnboardingFlowMode.FULL,
) : ResolveOnboardingFlowModeUseCase {
    override suspend fun invoke(): OnboardingFlowMode = mode
}
