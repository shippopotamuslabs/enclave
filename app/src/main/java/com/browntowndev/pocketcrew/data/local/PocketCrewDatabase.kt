package com.browntowndev.pocketcrew.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ChatEntity::class,
        MessageEntity::class,
        MessageSearch::class,
        ModelEntity::class,
        CrewPipelineStepEntity::class,
        ThinkingStepsEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(DateConverters::class, RoleConverters::class, ModelTypeConverters::class, MessageStateConverters::class)
abstract class PocketCrewDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun messageDao(): MessageDao
    abstract fun modelsDao(): ModelsDao
}
