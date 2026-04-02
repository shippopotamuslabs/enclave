package com.browntowndev.pocketcrew.core.data.local

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
        LocalModelEntity::class,
        LocalModelConfigurationEntity::class,
        ApiCredentialsEntity::class,
        ApiModelConfigurationEntity::class,
        DefaultModelEntity::class
    ],
    version = 3,
    exportSchema = true
)
@TypeConverters(DateConverters::class, RoleConverters::class, ModelTypeConverters::class, MessageStateConverters::class, PipelineStepConverters::class, ApiProviderConverters::class)
abstract class PocketCrewDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun messageDao(): MessageDao
    abstract fun localModelsDao(): LocalModelsDao
    abstract fun localModelConfigurationsDao(): LocalModelConfigurationsDao
    abstract fun apiCredentialsDao(): ApiCredentialsDao
    abstract fun apiModelConfigurationsDao(): ApiModelConfigurationsDao
    abstract fun defaultModelsDao(): DefaultModelsDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // DROP COLUMN is not supported in older SQLite versions.
                // Recreate the table instead.
                db.execSQL("""
                    CREATE TABLE local_models_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        model_file_format TEXT NOT NULL,
                        huggingface_model_name TEXT NOT NULL,
                        remote_filename TEXT NOT NULL,
                        local_filename TEXT NOT NULL,
                        sha256 TEXT NOT NULL,
                        size_in_bytes INTEGER NOT NULL,
                        vision_capable INTEGER NOT NULL,
                        model_status TEXT NOT NULL,
                        thinking_enabled INTEGER NOT NULL,
                        is_vision INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL
                    )
                """.trimIndent())
                
                db.execSQL("""
                    INSERT INTO local_models_new (
                        id, model_file_format, huggingface_model_name, remote_filename, 
                        local_filename, sha256, size_in_bytes, vision_capable, 
                        model_status, thinking_enabled, is_vision, updated_at
                    )
                    SELECT id, model_file_format, huggingface_model_name, remote_filename, 
                           local_filename, sha256, size_in_bytes, vision_capable, 
                           model_status, thinking_enabled, is_vision, updated_at
                    FROM local_models
                """.trimIndent())
                
                db.execSQL("DROP TABLE local_models")
                db.execSQL("ALTER TABLE local_models_new RENAME TO local_models")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_local_models_sha256 ON local_models(sha256)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE api_model_configurations ADD COLUMN min_p REAL NOT NULL DEFAULT 0.05")
                db.execSQL("ALTER TABLE api_model_configurations ADD COLUMN system_prompt TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}