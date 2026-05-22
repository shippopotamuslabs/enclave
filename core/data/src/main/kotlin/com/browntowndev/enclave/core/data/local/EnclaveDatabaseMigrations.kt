package com.browntowndev.enclave.core.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Incremental Room migrations. Keep DDL aligned with exported schemas under `schemas/`.
 */
object EnclaveDatabaseMigrations {
    /** Adds [MessageTranscriptSegmentEntity] storage for interleaved assistant transcripts. */
    val MIGRATION_3_4 =
        Migration(3, 4) { db: SupportSQLiteDatabase ->
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `message_transcript_segment` (
                    `id` TEXT NOT NULL,
                    `message_id` TEXT NOT NULL,
                    `ordinal` INTEGER NOT NULL,
                    `kind` TEXT NOT NULL,
                    `text` TEXT NOT NULL,
                    `thinking_start_time` INTEGER,
                    `thinking_end_time` INTEGER,
                    `thinking_duration_seconds` INTEGER,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`message_id`) REFERENCES `message`(`id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_message_transcript_segment_message_id` " +
                    "ON `message_transcript_segment` (`message_id`)",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_message_transcript_segment_message_id_ordinal` " +
                    "ON `message_transcript_segment` (`message_id`, `ordinal`)",
            )
        }

    /**
     * Adds sync columns and replaces the unique (message_id, ordinal) index with a non-unique index
     * so soft-deleted segment rows can coexist with new rows for the same ordinals after replace.
     */
    val MIGRATION_4_5 =
        Migration(4, 5) { db: SupportSQLiteDatabase ->
            db.execSQL(
                "ALTER TABLE message_transcript_segment ADD COLUMN pending_sync INTEGER NOT NULL DEFAULT 0",
            )
            db.execSQL(
                "ALTER TABLE message_transcript_segment ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0",
            )
            db.execSQL(
                "ALTER TABLE message_transcript_segment ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0",
            )
            db.execSQL("DROP INDEX IF EXISTS `index_message_transcript_segment_message_id_ordinal`")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_message_transcript_segment_message_id_ordinal` " +
                    "ON `message_transcript_segment` (`message_id`, `ordinal`)",
            )
        }

    /** Adds index to `albumId` in `studio_gallery` table. */
    val MIGRATION_5_6 =
        Migration(5, 6) { db: SupportSQLiteDatabase ->
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_studio_gallery_albumId` " +
                    "ON `studio_gallery` (`albumId`)",
            )
        }
}
