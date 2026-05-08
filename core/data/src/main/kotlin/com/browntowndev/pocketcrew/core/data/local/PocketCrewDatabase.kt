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
    version = 8,
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
    abstract fun messageFileDao(): MessageFileDao
    abstract fun cleanupDao(): CleanupDao

    companion object {
        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chat ADD COLUMN pending_sync INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE message ADD COLUMN pending_sync INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                listOf(
                    "message_file",
                    "message_vision_analysis",
                    "studio_gallery",
                    "studio_albums",
                    "api_credentials",
                    "api_model_configurations",
                    "local_model_configurations",
                    "default_models",
                    "local_models",
                    "memories",
                    "web_search_source",
                    "tts_providers",
                    "media_providers",
                ).forEach { table ->
                    db.execSQL("ALTER TABLE `$table` ADD COLUMN pending_sync INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("UPDATE `$table` SET pending_sync = 1")
                }
            }
        }

        val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Migrate studio_albums
                db.execSQL("""
                    CREATE TABLE studio_albums_new (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        pending_sync INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                db.execSQL("INSERT INTO studio_albums_new (id, name, pending_sync) SELECT CAST(id AS TEXT), name, pending_sync FROM studio_albums")
                db.execSQL("DROP TABLE studio_albums")
                db.execSQL("ALTER TABLE studio_albums_new RENAME TO studio_albums")

                // Migrate studio_gallery
                db.execSQL("""
                    CREATE TABLE studio_gallery_new (
                        id TEXT NOT NULL PRIMARY KEY,
                        prompt TEXT NOT NULL,
                        mediaUri TEXT NOT NULL,
                        mediaType TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        albumId TEXT,
                        remoteUrl TEXT,
                        remoteThumbnailUrl TEXT,
                        syncStatus TEXT NOT NULL DEFAULT 'PENDING_UPLOAD',
                        pending_sync INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(albumId) REFERENCES studio_albums(id) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO studio_gallery_new (id, prompt, mediaUri, mediaType, createdAt, albumId, pending_sync)
                    SELECT CAST(id AS TEXT), prompt, mediaUri, mediaType, createdAt, CAST(albumId AS TEXT), pending_sync FROM studio_gallery
                """)
                db.execSQL("DROP TABLE studio_gallery")
                db.execSQL("ALTER TABLE studio_gallery_new RENAME TO studio_gallery")
            }
        }

        val MIGRATION_7_8 = object : androidx.room.migration.Migration(7, 8) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                listOf(
                    "chat",
                    "message",
                    "memories",
                    "studio_albums",
                    "studio_gallery",
                    "message_file",
                    "message_vision_analysis",
                    "api_credentials",
                    "api_model_configurations",
                    "local_model_configurations",
                    "default_models",
                    "local_models",
                    "web_search_source",
                    "tts_providers",
                    "media_providers",
                ).forEach { table ->
                    // Check if column exists first to be safe (some might have updated_at already)
                    val cursor = db.query("PRAGMA table_info(`$table`)")
                    var hasUpdatedAt = false
                    var hasDeleted = false
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                        if (name == "updated_at") hasUpdatedAt = true
                        if (name == "deleted") hasDeleted = true
                    }
                    cursor.close()

                    if (!hasUpdatedAt) {
                        db.execSQL("ALTER TABLE `$table` ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0")
                    }
                    if (!hasDeleted) {
                        db.execSQL("ALTER TABLE `$table` ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0")
                    }
                }
            }
        }
    }
}
