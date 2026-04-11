package com.browntowndev.pocketcrew.core.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE message ADD COLUMN image_uri TEXT")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `message_vision_analysis` (
                `id` TEXT NOT NULL,
                `user_message_id` TEXT NOT NULL,
                `image_uri` TEXT NOT NULL,
                `prompt_text` TEXT NOT NULL,
                `analysis_text` TEXT NOT NULL,
                `model_type` TEXT NOT NULL,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`user_message_id`) REFERENCES `message`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_message_vision_analysis_user_message_id` ON `message_vision_analysis` (`user_message_id`)"
        )
        database.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_message_vision_analysis_user_message_id_image_uri` ON `message_vision_analysis` (`user_message_id`, `image_uri`)"
        )
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE local_models ADD COLUMN mmproj_remote_filename TEXT")
        database.execSQL("ALTER TABLE local_models ADD COLUMN mmproj_local_filename TEXT")
        database.execSQL("ALTER TABLE local_models ADD COLUMN mmproj_sha256 TEXT")
        database.execSQL("ALTER TABLE local_models ADD COLUMN mmproj_size_in_bytes INTEGER")
    }
}
