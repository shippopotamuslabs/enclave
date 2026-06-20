package com.browntowndev.enclave.core.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Explicit migrations that preserve user-created data for schema changes introduced after version 2.
 */
object EnclaveDatabaseMigrations {
    val MIGRATION_2_3 =
        object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE local_model_configurations " +
                        "ADD COLUMN reasoning_budget INTEGER NOT NULL DEFAULT 1024",
                )
                db.execSQL(
                    "UPDATE local_model_configurations " +
                        "SET reasoning_budget = MAX(1, context_window / 4)",
                )
            }
        }

    val MIGRATION_3_4 =
        object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE api_model_configurations ADD COLUMN openrouter_preferred_provider TEXT",
                )
            }
        }

    val MIGRATION_4_5 =
        object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE api_model_configurations " +
                        "ADD COLUMN include_reasoning_content_on_request INTEGER NOT NULL DEFAULT 0",
                )
            }
        }
}
