package com.browntowndev.pocketcrew.core.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PocketCrewDatabaseMigrationTest {

    @Test
    fun `database exposes expected tables and omits legacy ones`() {
        val db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PocketCrewDatabase::class.java
        ).allowMainThreadQueries().build()

        val sqliteDb = db.openHelper.writableDatabase

        fun hasTable(tableName: String): Boolean {
            val cursor = sqliteDb.query(
                "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
                arrayOf(tableName)
            )
            return cursor.use { it.count == 1 }
        }

        assertTrue(hasTable("local_models"))
        assertTrue(hasTable("local_model_configurations"))
        assertTrue(hasTable("api_credentials"))
        assertTrue(hasTable("api_model_configurations"))
        assertTrue(hasTable("default_models"))
        assertTrue(hasTable("chat_summary"))
        assertFalse(hasTable("models"))
        assertFalse(hasTable("api_models"))

        val cursor = sqliteDb.query("SELECT COUNT(*) FROM local_models")
        cursor.moveToFirst()
        assertEquals(0, cursor.getInt(0))
        cursor.close()

        val credentialsColumns = sqliteDb.query("PRAGMA table_info('api_credentials')")
        val credentialColumnNames = buildList {
            credentialsColumns.use {
                while (it.moveToNext()) {
                    add(it.getString(it.getColumnIndexOrThrow("name")))
                }
            }
        }
        assertTrue(credentialColumnNames.contains("api_key_signature"))

        val indexCursor = sqliteDb.query("PRAGMA index_list('api_credentials')")
        val indexNames = buildList {
            indexCursor.use {
                while (it.moveToNext()) {
                    add(it.getString(it.getColumnIndexOrThrow("name")))
                }
            }
        }
        assertTrue(indexNames.contains("index_api_credentials_api_key_signature"))

        db.close()
    }

    @Test
    fun `api credentials schema does not keep identity uniqueness index`() {
        val db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PocketCrewDatabase::class.java
        ).allowMainThreadQueries().build()
        try {
            val sqliteDb = db.openHelper.readableDatabase
            val cursor = sqliteDb.query("PRAGMA index_list('api_credentials')")
            val indexNames = buildList {
                cursor.use {
                    while (it.moveToNext()) {
                        add(it.getString(it.getColumnIndexOrThrow("name")))
                    }
                }
            }

            assertFalse(indexNames.contains("index_api_credentials_provider_model_id_base_url"))
        } finally {
            db.close()
        }
    }

    @Test
    fun `tavily_source table has extracted column with default false`() {
        val db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PocketCrewDatabase::class.java
        ).allowMainThreadQueries().build()

        try {
            val sqliteDb = db.openHelper.readableDatabase

            val cursor = sqliteDb.query("PRAGMA table_info('tavily_source')")
            val extractedColumn = buildMap {
                cursor.use {
                    while (it.moveToNext()) {
                        val name = it.getString(it.getColumnIndexOrThrow("name"))
                        val defaultValue = it.getString(it.getColumnIndexOrThrow("dflt_value"))
                        put(name, defaultValue)
                    }
                }
            }

            assertTrue("extracted column should exist", extractedColumn.containsKey("extracted"))
            assertEquals("extracted should default to 0 (false)", "0", extractedColumn["extracted"])
        } finally {
            db.close()
        }
    }
}
