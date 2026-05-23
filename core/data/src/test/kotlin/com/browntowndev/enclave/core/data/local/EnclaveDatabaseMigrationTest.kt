package com.browntowndev.enclave.core.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class EnclaveDatabaseMigrationTest {
    @Test
    fun `database exposes expected tables and omits legacy ones`() {
        val db =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    EnclaveDatabase::class.java,
                ).allowMainThreadQueries()
                .build()

        val sqliteDb = db.openHelper.writableDatabase

        fun hasTable(tableName: String): Boolean {
            val cursor =
                sqliteDb.query(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
                    arrayOf(tableName),
                )
            return cursor.use { it.count == 1 }
        }

        assertTrue(hasTable("local_models"))
        assertTrue(hasTable("local_model_configurations"))
        assertTrue(hasTable("api_credentials"))
        assertTrue(hasTable("api_model_configurations"))
        assertTrue(hasTable("default_models"))
        assertTrue(hasTable("chat_summary"))
        assertTrue(hasTable("message_transcript_segment"))
        assertFalse(hasTable("models"))
        assertFalse(hasTable("api_models"))

        val cursor = sqliteDb.query("SELECT COUNT(*) FROM local_models")
        cursor.moveToFirst()
        assertEquals(0, cursor.getInt(0))
        cursor.close()

        val credentialsColumns = sqliteDb.query("PRAGMA table_info('api_credentials')")
        val credentialColumnNames =
            buildList {
                credentialsColumns.use {
                    while (it.moveToNext()) {
                        add(it.getString(it.getColumnIndexOrThrow("name")))
                    }
                }
            }
        assertTrue(credentialColumnNames.contains("api_key_signature"))

        val indexCursor = sqliteDb.query("PRAGMA index_list('api_credentials')")
        val indexNames =
            buildList {
                indexCursor.use {
                    while (it.moveToNext()) {
                        add(it.getString(it.getColumnIndexOrThrow("name")))
                    }
                }
            }
        assertTrue(indexNames.contains("index_api_credentials_api_key_signature"))

        val localModelsColumns = sqliteDb.query("PRAGMA table_info('local_models')")
        val localModelColumnNames =
            buildList {
                localModelsColumns.use {
                    while (it.moveToNext()) {
                        add(it.getString(it.getColumnIndexOrThrow("name")))
                    }
                }
            }
        assertTrue(localModelColumnNames.contains("display_name"))

        db.close()
    }

    @Test
    @org.junit.jupiter.api.DisplayName("SCN-SCHEMA-001")
    fun scnSchema001_freshVersion1DatabaseCreatesUtilityModelsTable() {
        val db =
            Room
                .databaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    EnclaveDatabase::class.java,
                    "utility_models_schema_v1.db",
                ).fallbackToDestructiveMigration(dropAllTables = true)
                .allowMainThreadQueries()
                .build()
        try {
            val sqliteDb = db.openHelper.writableDatabase
            val columns =
                buildList {
                    val cursor = sqliteDb.query("PRAGMA table_info(utility_models)")
                    cursor.use {
                        while (it.moveToNext()) {
                            add(it.getString(it.getColumnIndexOrThrow("name")))
                        }
                    }
                }
            assertTrue(columns.contains("utility_type"))
            assertTrue(columns.contains("sha256"))
            assertTrue(columns.contains("pending_sync"))
            assertTrue(columns.contains("deleted"))

            val indexCursor = sqliteDb.query("PRAGMA index_list('utility_models')")
            var uniqueUtilityType = false
            indexCursor.use {
                while (it.moveToNext()) {
                    val indexName = it.getString(it.getColumnIndexOrThrow("name"))
                    val unique = it.getInt(it.getColumnIndexOrThrow("unique")) == 1
                    if (indexName == "index_utility_models_utility_type" && unique) {
                        uniqueUtilityType = true
                    }
                }
            }
            assertTrue(uniqueUtilityType)
        } finally {
            db.close()
        }
    }

    @Test
    fun `api credentials schema does not keep identity uniqueness index`() {
        val db =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    EnclaveDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
        try {
            val sqliteDb = db.openHelper.readableDatabase
            val cursor = sqliteDb.query("PRAGMA index_list('api_credentials')")
            val indexNames =
                buildList {
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
    fun `migration 5-6 adds pending_sync column to all syncable tables`() {
        val db =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    EnclaveDatabase::class.java,
                ).allowMainThreadQueries()
                .build()

        try {
            val sqliteDb = db.openHelper.readableDatabase
            val syncableTables =
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
                    "message_transcript_segment",
                    "tts_providers",
                    "media_providers",
                )
            for (table in syncableTables) {
                val cursor = sqliteDb.query("PRAGMA table_info('$table')")
                val columnNames =
                    buildList {
                        cursor.use {
                            while (it.moveToNext()) {
                                add(it.getString(it.getColumnIndexOrThrow("name")))
                            }
                        }
                    }
                assertTrue(columnNames.contains("pending_sync"), "$table should have pending_sync column")
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun `migration 5-6 pending_sync column defaults to 0`() {
        val db =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    EnclaveDatabase::class.java,
                ).allowMainThreadQueries()
                .build()

        try {
            val sqliteDb = db.openHelper.readableDatabase
            val cursor = sqliteDb.query("PRAGMA table_info('memories')")
            val pendingSyncDefault =
                buildMap {
                    cursor.use {
                        while (it.moveToNext()) {
                            val name = it.getString(it.getColumnIndexOrThrow("name"))
                            val dflt = it.getString(it.getColumnIndexOrThrow("dflt_value"))
                            put(name, dflt)
                        }
                    }
                }
            assertEquals("0", pendingSyncDefault["pending_sync"])
        } finally {
            db.close()
        }
    }

    @Test
    fun `web_search_source table has extracted column with default false`() {
        val db =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    EnclaveDatabase::class.java,
                ).allowMainThreadQueries()
                .build()

        try {
            val sqliteDb = db.openHelper.readableDatabase

            val cursor = sqliteDb.query("PRAGMA table_info('web_search_source')")
            val extractedColumn =
                buildMap {
                    cursor.use {
                        while (it.moveToNext()) {
                            val name = it.getString(it.getColumnIndexOrThrow("name"))
                            val defaultValue = it.getString(it.getColumnIndexOrThrow("dflt_value"))
                            put(name, defaultValue)
                        }
                    }
                }

            assertTrue(extractedColumn.containsKey("extracted"), "extracted column should exist")
            assertEquals("0", extractedColumn["extracted"], "extracted should default to 0 (false)")
        } finally {
            db.close()
        }
    }
}
