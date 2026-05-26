package com.browntowndev.enclave.feature.inference.di

import com.browntowndev.enclave.feature.inference.telemetry.ProviderCostEstimator
import com.browntowndev.enclave.feature.inference.telemetry.ProviderTelemetryNormalizer
import com.browntowndev.enclave.feature.inference.telemetry.SavedApiModelPricingEstimator
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
    fun provideSavedApiModelPricingEstimator(): SavedApiModelPricingEstimator = SavedApiModelPricingEstimator()

    @Provides
    @Singleton
    fun provideProviderCostEstimator(
        savedPricingEstimator: SavedApiModelPricingEstimator,
    ): ProviderCostEstimator = ProviderCostEstimator(savedPricingEstimator)

    @Provides
    @Singleton
    fun provideProviderTelemetryNormalizer(
        costEstimator: ProviderCostEstimator,
    ): ProviderTelemetryNormalizer = ProviderTelemetryNormalizer(costEstimator)
}
