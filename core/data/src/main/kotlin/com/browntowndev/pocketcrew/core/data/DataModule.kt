package com.browntowndev.pocketcrew.core.data

import android.app.NotificationManager
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import androidx.work.WorkManager
import com.browntowndev.pocketcrew.core.data.download.DownloadNotificationManager
import com.browntowndev.pocketcrew.core.data.download.DownloadProgressTracker
import com.browntowndev.pocketcrew.core.data.download.DownloadSessionManager
import com.browntowndev.pocketcrew.core.data.download.DownloadSpeedTracker
import com.browntowndev.pocketcrew.core.data.download.DownloadStateManager
import com.browntowndev.pocketcrew.core.data.download.DownloadWorkScheduler
import com.browntowndev.pocketcrew.core.data.download.HashingService
import com.browntowndev.pocketcrew.core.data.download.ModelConfigFetcherImpl
import com.browntowndev.pocketcrew.core.data.download.ModelDownloadOrchestratorImpl
import com.browntowndev.pocketcrew.core.data.download.ModelFileScanner
import com.browntowndev.pocketcrew.core.data.download.WorkProgressParser
import com.browntowndev.pocketcrew.core.data.download.remote.HttpFileDownloader
import com.browntowndev.pocketcrew.core.data.download.remote.HuggingFaceModelUrlProvider
import com.browntowndev.pocketcrew.core.data.local.ApiCredentialsDao
import com.browntowndev.pocketcrew.core.data.local.ApiModelConfigurationsDao
import com.browntowndev.pocketcrew.core.data.local.ChatDao
import com.browntowndev.pocketcrew.core.data.local.DefaultModelsDao
import com.browntowndev.pocketcrew.core.data.local.LocalModelConfigurationsDao
import com.browntowndev.pocketcrew.core.data.local.LocalModelsDao
import com.browntowndev.pocketcrew.core.data.local.MessageDao
import com.browntowndev.pocketcrew.core.data.local.PocketCrewDatabase
import com.browntowndev.pocketcrew.core.data.repository.ApiModelRepositoryImpl
import com.browntowndev.pocketcrew.core.data.repository.ChatRepositoryImpl
import com.browntowndev.pocketcrew.core.data.repository.DefaultModelRepositoryImpl
import com.browntowndev.pocketcrew.core.data.repository.DeviceEnvironmentRepository
import com.browntowndev.pocketcrew.core.data.repository.MessageRepositoryImpl
import com.browntowndev.pocketcrew.core.data.repository.ModelConfigProviderImpl
import com.browntowndev.pocketcrew.core.data.repository.ModelRegistryImpl
import com.browntowndev.pocketcrew.core.data.repository.PipelineStateRepositoryImpl
import com.browntowndev.pocketcrew.core.data.repository.RoomTransactionProvider
import com.browntowndev.pocketcrew.core.data.repository.SettingsRepositoryImpl
import com.browntowndev.pocketcrew.domain.port.download.DownloadSpeedTrackerPort
import com.browntowndev.pocketcrew.domain.port.download.FileDownloaderPort
import com.browntowndev.pocketcrew.domain.port.download.HashingPort
import com.browntowndev.pocketcrew.domain.port.download.ModelDownloadOrchestratorPort
import com.browntowndev.pocketcrew.domain.port.download.ModelFileScannerPort
import com.browntowndev.pocketcrew.domain.port.download.ModelUrlProviderPort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.ApiModelRepositoryPort
import com.browntowndev.pocketcrew.domain.port.repository.ChatRepository
import com.browntowndev.pocketcrew.domain.port.repository.DefaultModelRepositoryPort
import com.browntowndev.pocketcrew.domain.port.repository.DeviceEnvironmentRepositoryPort
import com.browntowndev.pocketcrew.domain.port.repository.MessageRepository
import com.browntowndev.pocketcrew.domain.port.repository.ModelConfigFetcherPort
import com.browntowndev.pocketcrew.domain.port.repository.ModelConfigProvider
import com.browntowndev.pocketcrew.domain.port.repository.ModelRegistryPort
import com.browntowndev.pocketcrew.domain.port.repository.PipelineStateRepository
import com.browntowndev.pocketcrew.domain.port.repository.SettingsRepository
import com.browntowndev.pocketcrew.domain.port.repository.TransactionProvider
import com.browntowndev.pocketcrew.domain.qualifier.PipelineDataStore
import com.browntowndev.pocketcrew.domain.util.Clock
import com.browntowndev.pocketcrew.domain.util.SystemClock
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import okhttp3.OkHttpClient

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
private val Context.pipelineDataStore: DataStore<Preferences> by preferencesDataStore(name = "pipeline_state")

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun providePocketCrewDatabase(@ApplicationContext context: Context): PocketCrewDatabase {
        return Room.databaseBuilder(
            context,
            PocketCrewDatabase::class.java,
            "pocket_crew_db"
        )
        .fallbackToDestructiveMigration()
        .build()
    }

    @Provides
    fun provideChatDao(database: PocketCrewDatabase): ChatDao = database.chatDao()

    @Provides
    fun provideMessageDao(database: PocketCrewDatabase): MessageDao = database.messageDao()

    @Provides
    fun provideLocalModelsDao(database: PocketCrewDatabase): LocalModelsDao = database.localModelsDao()

    @Provides
    fun provideLocalModelConfigurationsDao(database: PocketCrewDatabase): LocalModelConfigurationsDao = database.localModelConfigurationsDao()

    @Provides
    fun provideApiCredentialsDao(database: PocketCrewDatabase): ApiCredentialsDao = database.apiCredentialsDao()

    @Provides
    fun provideApiModelConfigurationsDao(database: PocketCrewDatabase): ApiModelConfigurationsDao = database.apiModelConfigurationsDao()

    @Provides
    fun provideDefaultModelsDao(database: PocketCrewDatabase): DefaultModelsDao = database.defaultModelsDao()

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> = context.dataStore

    @Provides
    @Singleton
    @PipelineDataStore
    fun providePipelineDataStore(@ApplicationContext context: Context): DataStore<Preferences> = context.pipelineDataStore

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager = WorkManager.getInstance(context)

    @Provides
    @Singleton
    fun provideNotificationManager(@ApplicationContext context: Context): DownloadNotificationManager {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return DownloadNotificationManager(context, notificationManager)
    }

    @Provides
    @Singleton
    fun provideSystemClock(): Clock = SystemClock()

    @Provides
    @Singleton
    fun provideDownloadSpeedTracker(clock: Clock): DownloadSpeedTrackerPort = DownloadSpeedTracker(clock)

    @Provides
    @Singleton
    fun provideDownloadProgressTracker(speedTracker: DownloadSpeedTrackerPort): DownloadProgressTracker {
        return DownloadProgressTracker(speedTracker)
    }

    @Provides
    @Singleton
    fun provideHashingService(): HashingPort = HashingService()

    @Provides
    @Singleton
    fun provideHttpFileDownloader(okHttpClient: OkHttpClient, logger: LoggingPort): FileDownloaderPort = HttpFileDownloader(okHttpClient, logger)

    @Provides
    @Singleton
    fun provideHuggingFaceModelUrlProvider(): ModelUrlProviderPort = HuggingFaceModelUrlProvider()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DataRepositoryModule {

    @Binds
    @Singleton
    abstract fun bindChatRepository(impl: ChatRepositoryImpl): ChatRepository

    @Binds
    @Singleton
    abstract fun bindMessageRepository(impl: MessageRepositoryImpl): MessageRepository

    @Binds
    @Singleton
    abstract fun bindDeviceEnvironmentRepository(impl: DeviceEnvironmentRepository): DeviceEnvironmentRepositoryPort

    @Binds
    @Singleton
    abstract fun bindModelRegistry(impl: ModelRegistryImpl): ModelRegistryPort

    @Binds
    @Singleton
    abstract fun bindModelConfigProvider(impl: ModelConfigProviderImpl): ModelConfigProvider

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindPipelineStateRepository(impl: PipelineStateRepositoryImpl): PipelineStateRepository

    @Binds
    @Singleton
    abstract fun bindTransactionProvider(impl: RoomTransactionProvider): TransactionProvider

    @Binds
    @Singleton
    abstract fun bindModelDownloadOrchestrator(impl: ModelDownloadOrchestratorImpl): ModelDownloadOrchestratorPort

    @Binds
    @Singleton
    abstract fun bindModelFileScanner(impl: ModelFileScanner): ModelFileScannerPort

    @Binds
    @Singleton
    abstract fun bindModelConfigFetcher(impl: ModelConfigFetcherImpl): ModelConfigFetcherPort

    @Binds
    @Singleton
    abstract fun bindDefaultModelRepository(impl: DefaultModelRepositoryImpl): DefaultModelRepositoryPort

    @Binds
    @Singleton
    abstract fun bindApiModelRepository(impl: ApiModelRepositoryImpl): ApiModelRepositoryPort
}