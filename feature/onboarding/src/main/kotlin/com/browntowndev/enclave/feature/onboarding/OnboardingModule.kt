package com.browntowndev.enclave.feature.onboarding

import com.browntowndev.enclave.domain.usecase.onboarding.BulkAssignApiProviderUseCase
import com.browntowndev.enclave.domain.usecase.onboarding.BulkAssignApiProviderUseCaseImpl
import com.browntowndev.enclave.domain.usecase.onboarding.ResolveOnboardingFlowModeUseCase
import com.browntowndev.enclave.domain.usecase.onboarding.ResolveOnboardingFlowModeUseCaseImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class OnboardingModule {
    @Binds
    @Singleton
    abstract fun bindBulkAssignApiProviderUseCase(impl: BulkAssignApiProviderUseCaseImpl): BulkAssignApiProviderUseCase

    @Binds
    @Singleton
    abstract fun bindResolveOnboardingFlowModeUseCase(
        impl: ResolveOnboardingFlowModeUseCaseImpl,
    ): ResolveOnboardingFlowModeUseCase
}
