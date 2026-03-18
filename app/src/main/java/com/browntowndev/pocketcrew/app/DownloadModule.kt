package com.browntowndev.pocketcrew.app

import android.app.NotificationManager
import android.content.Context
import androidx.work.WorkManager
import com.browntowndev.pocketcrew.data.download.DownloadNotificationManager
import com.browntowndev.pocketcrew.data.download.ModelConfigFetcherImpl
import com.browntowndev.pocketcrew.data.download.ModelDownloadOrchestratorImpl
import com.browntowndev.pocketcrew.data.download.DownloadSpeedTracker
import com.browntowndev.pocketcrew.data.download.remote.HuggingFaceModelUrlProvider
import com.browntowndev.pocketcrew.data.repository.ModelConfigProviderImpl
import com.browntowndev.pocketcrew.data.download.DownloadProgressTracker
import com.browntowndev.pocketcrew.domain.model.download.ModelConfig
import com.browntowndev.pocketcrew.domain.port.download.DownloadSpeedTrackerPort
import com.browntowndev.pocketcrew.domain.port.download.ModelDownloadOrchestratorPort
import com.browntowndev.pocketcrew.domain.port.download.ModelUrlProviderPort
import com.browntowndev.pocketcrew.domain.port.repository.ModelConfigProvider
import com.browntowndev.pocketcrew.domain.port.repository.ModelConfigFetcherPort
import com.browntowndev.pocketcrew.util.formatBytes
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
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

    @Provides
    @Singleton
    fun provideModelConfigProvider(
        @ApplicationContext context: Context,
        modelConfig: ModelConfig
    ): ModelConfigProvider {
        return ModelConfigProviderImpl(context, modelConfig)
    }

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager {
        return WorkManager.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideNotificationManager(
        @ApplicationContext context: Context
    ): NotificationManager {
        return context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    @Provides
    @Singleton
    fun provideDownloadNotificationManager(
        @ApplicationContext context: Context,
        notificationManager: NotificationManager
    ): DownloadNotificationManager {
        return DownloadNotificationManager(context, notificationManager)
    }

    @Provides
    @Singleton
    fun provideDownloadProgressTracker(
        downloadSpeedTracker: DownloadSpeedTracker
    ): DownloadProgressTracker {
        return DownloadProgressTracker(downloadSpeedTracker, ::formatBytes)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DownloadPortModule {

    @Binds
    @Singleton
    abstract fun bindModelConfigFetcherPort(
        modelConfigFetcherImpl: ModelConfigFetcherImpl
    ): ModelConfigFetcherPort

    @Binds
    @Singleton
    abstract fun bindDownloadSpeedTrackerPort(
        downloadSpeedTracker: DownloadSpeedTracker
    ): DownloadSpeedTrackerPort

    @Binds
    @Singleton
    abstract fun bindModelDownloadOrchestratorPort(
        modelDownloadOrchestratorImpl: ModelDownloadOrchestratorImpl
    ): ModelDownloadOrchestratorPort

    @Binds
    @Singleton
    abstract fun bindModelUrlProviderPort(
        huggingFaceModelUrlProvider: HuggingFaceModelUrlProvider
    ): ModelUrlProviderPort
}
