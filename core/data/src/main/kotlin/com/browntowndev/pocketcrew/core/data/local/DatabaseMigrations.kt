package com.browntowndev.pocketcrew.core.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.MessageId

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `chat_summary` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `chat_id` TEXT NOT NULL,
                `content` TEXT NOT NULL,
                `last_summarized_message_id` TEXT,
                `created_at` INTEGER NOT NULL,
                FOREIGN KEY(`chat_id`) REFERENCES `chat`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`last_summarized_message_id`) REFERENCES `message`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_chat_summary_chat_id` ON `chat_summary` (`chat_id`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_chat_summary_last_summarized_message_id` ON `chat_summary` (`last_summarized_message_id`)"
        )
    }
}
