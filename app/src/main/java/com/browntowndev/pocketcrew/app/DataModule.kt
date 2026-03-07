package com.browntowndev.pocketcrew.app

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.browntowndev.pocketcrew.data.local.ChatDao
import com.browntowndev.pocketcrew.data.local.MessageDao
import com.browntowndev.pocketcrew.data.local.ModelsDao
import com.browntowndev.pocketcrew.data.local.PocketCrewDatabase
import com.browntowndev.pocketcrew.data.download.HashingService
import com.browntowndev.pocketcrew.data.download.remote.HttpFileDownloader
import com.browntowndev.pocketcrew.data.repository.ChatRepositoryImpl
import com.browntowndev.pocketcrew.data.repository.MessageRepositoryImpl
import com.browntowndev.pocketcrew.data.repository.ModelRegistryImpl
import com.browntowndev.pocketcrew.data.repository.RoomTransactionProvider
import com.browntowndev.pocketcrew.data.repository.SettingsRepositoryImpl
import com.browntowndev.pocketcrew.domain.port.HashingPort
import com.browntowndev.pocketcrew.domain.port.download.FileDownloaderPort
import com.browntowndev.pocketcrew.domain.port.repository.ChatRepository
import com.browntowndev.pocketcrew.domain.port.repository.MessageRepository
import com.browntowndev.pocketcrew.domain.port.repository.ModelRegistryPort
import com.browntowndev.pocketcrew.domain.port.repository.SettingsRepository
import com.browntowndev.pocketcrew.domain.port.repository.TransactionProvider
import com.browntowndev.pocketcrew.inference.AndroidLoggingAdapter
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDataStore(
        @ApplicationContext context: Context
    ): DataStore<Preferences> {
        return context.dataStore
    }

    @Provides
    @Singleton
    fun providePocketCrewDatabase(
        @ApplicationContext context: Context
    ): PocketCrewDatabase {
        return Room.databaseBuilder(
            context,
            PocketCrewDatabase::class.java,
            "pocket_crew_db"
        ).build()
    }

    @Provides
    @Singleton
    fun provideMessageDao(database: PocketCrewDatabase): MessageDao {
        return database.messageDao()
    }

    @Provides
    @Singleton
    fun provideChatDao(database: PocketCrewDatabase): ChatDao {
        return database.chatDao()
    }

    @Provides
    @Singleton
    fun provideModelsDao(database: PocketCrewDatabase): ModelsDao {
        return database.modelsDao()
    }

}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        settingsRepositoryImpl: SettingsRepositoryImpl
    ): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindMessageRepository(
        messageRepositoryImpl: MessageRepositoryImpl
    ): MessageRepository

    @Binds
    @Singleton
    abstract fun bindChatRepository(
        chatRepositoryImpl: ChatRepositoryImpl
    ): ChatRepository

    @Binds
    @Singleton
    abstract fun bindTransactionProvider(
        roomTransactionProvider: RoomTransactionProvider
    ): TransactionProvider

    @Binds
    @Singleton
    abstract fun bindModelRegistry(
        modelRegistryImpl: ModelRegistryImpl
    ): ModelRegistryPort

    @Binds
    @Singleton
    abstract fun bindHashingPort(
        hashingService: HashingService
    ): HashingPort

    @Binds
    @Singleton
    abstract fun bindLoggingPort(
        androidLoggingAdapter: AndroidLoggingAdapter
    ): LoggingPort

    @Binds
    @Singleton
    abstract fun bindFileDownloaderPort(
        httpFileDownloader: HttpFileDownloader
    ): FileDownloaderPort
}
