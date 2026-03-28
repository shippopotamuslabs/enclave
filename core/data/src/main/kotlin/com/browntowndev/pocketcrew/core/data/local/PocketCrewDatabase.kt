package com.browntowndev.pocketcrew.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        ChatEntity::class,
        MessageEntity::class,
        MessageSearch::class,
        ModelEntity::class,
        ApiModelEntity::class,
        DefaultModelEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(DateConverters::class, RoleConverters::class, ModelTypeConverters::class, MessageStateConverters::class, PipelineStepConverters::class, ApiProviderConverters::class, ModelSourceConverters::class)
abstract class PocketCrewDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun messageDao(): MessageDao
    abstract fun modelsDao(): ModelsDao
    abstract fun apiModelsDao(): ApiModelsDao
    abstract fun defaultModelsDao(): DefaultModelsDao
}
