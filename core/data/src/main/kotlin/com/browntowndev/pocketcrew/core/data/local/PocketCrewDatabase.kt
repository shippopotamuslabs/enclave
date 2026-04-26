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
        MemoriesEntity::class,
    ],
    version = 3,
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
    abstract fun memoriesDao(): MemoriesDao

    companion object {
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tts_providers ADD COLUMN modelName TEXT")
            }
        }

        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS memories (
                        id TEXT PRIMARY KEY NOT NULL,
                        category TEXT NOT NULL,
                        content TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }
    }
}
