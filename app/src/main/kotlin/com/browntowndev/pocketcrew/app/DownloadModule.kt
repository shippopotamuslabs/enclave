package com.browntowndev.pocketcrew.app
import com.browntowndev.pocketcrew.core.data.repository.ModelConfigProviderImpl
import com.browntowndev.pocketcrew.domain.model.download.ModelConfig
import com.browntowndev.pocketcrew.domain.port.repository.ModelConfigProvider
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
    fun provideModelConfig(): ModelConfig {
        return ModelConfig
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DownloadPortModule {

    @Binds
    @Singleton
    abstract fun bindModelConfigProvider(
        modelConfigProviderImpl: ModelConfigProviderImpl
    ): ModelConfigProvider
}
