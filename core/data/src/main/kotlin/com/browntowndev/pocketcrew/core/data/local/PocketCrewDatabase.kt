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
        TavilySourceEntity::class,
        MessageVisionAnalysisEntity::class,
        EmbeddingEntity::class,
        TtsProviderEntity::class,
    ],
    version = 2,
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
    abstract fun tavilySourceDao(): TavilySourceDao
    abstract fun messageVisionAnalysisDao(): MessageVisionAnalysisDao
    abstract fun embeddingDao(): EmbeddingDao
    abstract fun ttsProviderDao(): TtsProviderDao

    companion object {
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tts_providers ADD COLUMN modelName TEXT")
            }
        }
    }
}
