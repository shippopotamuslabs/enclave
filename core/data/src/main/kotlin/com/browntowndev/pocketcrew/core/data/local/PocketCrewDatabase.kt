package com.browntowndev.pocketcrew.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        ChatEntity::class,
        MessageEntity::class,
        ChatSummaryEntity::class,
        LocalModelEntity::class,
        LocalModelConfigurationEntity::class,
        ApiModelConfigurationEntity::class,
        ApiCredentialsEntity::class,
        DefaultModelEntity::class,
        WebSearchSourceEntity::class,
        MessageVisionAnalysisEntity::class,
        EmbeddingEntity::class,
        TtsProviderEntity::class,
        MemoriesEntity::class,
        MediaProviderEntity::class,
        StudioMediaEntity::class,
        StudioAlbumEntity::class,
        MessageFileEntity::class,
    ],
    version = 5,
    exportSchema = false
)
@TypeConverters(ModelTypeConverters::class)
abstract class PocketCrewDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun messageDao(): MessageDao
    abstract fun chatSummaryDao(): ChatSummaryDao
    abstract fun localModelsDao(): LocalModelsDao
    abstract fun localModelConfigurationsDao(): LocalModelConfigurationsDao
    abstract fun apiModelConfigurationsDao(): ApiModelConfigurationsDao
    abstract fun apiCredentialsDao(): ApiCredentialsDao
    abstract fun defaultModelsDao(): DefaultModelsDao
    abstract fun webSearchSourceDao(): WebSearchSourceDao
    abstract fun messageVisionAnalysisDao(): MessageVisionAnalysisDao
    abstract fun embeddingDao(): EmbeddingDao
    abstract fun ttsProviderDao(): TtsProviderDao
    abstract fun memoriesDao(): MemoriesDao
    abstract fun mediaProviderDao(): MediaProviderDao
    abstract fun studioMediaDao(): StudioMediaDao
    abstract fun studioAlbumDao(): StudioAlbumDao
    abstract fun cleanupDao(): CleanupDao

    companion object {
        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chat ADD COLUMN pending_sync INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE message ADD COLUMN pending_sync INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
