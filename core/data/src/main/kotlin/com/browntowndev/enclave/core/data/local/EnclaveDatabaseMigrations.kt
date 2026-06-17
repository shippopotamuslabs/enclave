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
}
