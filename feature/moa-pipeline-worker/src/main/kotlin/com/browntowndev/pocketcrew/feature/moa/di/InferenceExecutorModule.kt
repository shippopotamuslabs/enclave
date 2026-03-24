package com.browntowndev.pocketcrew.feature.moa.di

import com.browntowndev.pocketcrew.domain.port.inference.PipelineExecutorPort
import com.browntowndev.pocketcrew.feature.moa.InferenceServicePipelineExecutor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Module for binding inference pipeline executor.
 * Uses InferenceServicePipelineExecutor which runs the Crew pipeline
 * via a custom foreground Service with specialUse type (no quota limits).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class InferenceExecutorModule {

    @Binds
    @Singleton
    abstract fun bindPipelineExecutor(
        inferenceServicePipelineExecutor: InferenceServicePipelineExecutor
    ): PipelineExecutorPort
}
