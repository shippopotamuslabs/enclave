package com.browntowndev.enclave.feature.inference.di

import com.browntowndev.enclave.feature.inference.telemetry.ProviderCostEstimator
import com.browntowndev.enclave.feature.inference.telemetry.ProviderPricingCatalog
import com.browntowndev.enclave.feature.inference.telemetry.ProviderTelemetryNormalizer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object InferenceTelemetryModule {
    @Provides
    @Singleton
    fun provideProviderPricingCatalog(): ProviderPricingCatalog = ProviderPricingCatalog()

    @Provides
    @Singleton
    fun provideProviderCostEstimator(catalog: ProviderPricingCatalog): ProviderCostEstimator =
        ProviderCostEstimator(catalog)

    @Provides
    @Singleton
    fun provideProviderTelemetryNormalizer(
        costEstimator: ProviderCostEstimator,
    ): ProviderTelemetryNormalizer = ProviderTelemetryNormalizer(costEstimator)
}
