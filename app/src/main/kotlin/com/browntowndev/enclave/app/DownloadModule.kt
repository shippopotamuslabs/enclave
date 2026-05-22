package com.browntowndev.enclave.app

import com.browntowndev.enclave.core.data.download.DownloadWorkScheduler
import com.browntowndev.enclave.core.data.repository.ModelConfigProviderImpl
import com.browntowndev.enclave.domain.model.download.ModelConfig
import com.browntowndev.enclave.domain.port.download.DownloadWorkSchedulerPort
import com.browntowndev.enclave.domain.port.repository.ModelConfigProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DownloadModule {
    @Provides
    @Singleton
    fun provideModelConfig(): ModelConfig = ModelConfig
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DownloadPortModule {
    @Binds
    @Singleton
    abstract fun bindModelConfigProvider(modelConfigProviderImpl: ModelConfigProviderImpl): ModelConfigProvider

    @Binds
    @Singleton
    abstract fun bindDownloadWorkSchedulerPort(impl: DownloadWorkScheduler): DownloadWorkSchedulerPort
}
