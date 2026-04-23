package com.browntowndev.pocketcrew.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters


@Database(
    entities = [
        ChatEntity::class,
        MessageEntity::class,
        MessageVisionAnalysisEntity::class,
        LocalModelEntity::class,
        LocalModelConfigurationEntity::class,
        ApiCredentialsEntity::class,
        ApiModelConfigurationEntity::class,
        DefaultModelEntity::class,
        TavilySourceEntity::class,
        ChatSummaryEntity::class
    ],
    version = 3,
    exportSchema = true
)
@TypeConverters(DateConverters::class, RoleConverters::class, ModelTypeConverters::class, MessageStateConverters::class, PipelineStepConverters::class, ApiProviderConverters::class, IdTypeConverters::class)
abstract class PocketCrewDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun messageDao(): MessageDao
    abstract fun chatSummaryDao(): ChatSummaryDao
    abstract fun tavilySourceDao(): TavilySourceDao
    abstract fun messageVisionAnalysisDao(): MessageVisionAnalysisDao
    abstract fun localModelsDao(): LocalModelsDao
    abstract fun localModelConfigurationsDao(): LocalModelConfigurationsDao
    abstract fun apiCredentialsDao(): ApiCredentialsDao
    abstract fun apiModelConfigurationsDao(): ApiModelConfigurationsDao
    abstract fun defaultModelsDao(): DefaultModelsDao
    abstract fun embeddingDao(): EmbeddingDao
}
