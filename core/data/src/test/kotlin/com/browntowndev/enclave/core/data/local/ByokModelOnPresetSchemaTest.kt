package com.browntowndev.enclave.core.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class ByokModelOnPresetSchemaTest {
    private lateinit var database: EnclaveDatabase

    @BeforeEach
    fun setup() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    EnclaveDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
    }

    @AfterEach
    fun teardown() {
        database.close()
    }

    @Test
    fun `SCN-SCHEMA-001 Credentials Table Without Model Columns`() {
        assertEquals(1, database.openHelper.readableDatabase.version)
        val columns = tableColumns("api_credentials")
        assertFalse("model_id" in columns)
        assertFalse("is_multimodal" in columns)
    }

    @Test
    fun `SCN-SCHEMA-002 Preset Table With Model Columns`() {
        val columns = tableInfo("api_model_configurations")
        val modelId = columns.firstOrNull { it.name == "model_id" }
        val isMultimodal = columns.firstOrNull { it.name == "is_multimodal" }
        assertTrue(modelId != null)
        assertEquals("TEXT", modelId?.type)
        assertEquals(1, modelId?.notNull)
        assertTrue(isMultimodal != null)
        assertEquals(1, isMultimodal?.notNull)
    }

    private fun tableColumns(table: String): Set<String> = tableInfo(table).map { it.name }.toSet()

    private fun tableInfo(table: String): List<ColumnInfo> {
        val cursor = database.openHelper.readableDatabase.query("PRAGMA table_info($table)")
        val result = mutableListOf<ColumnInfo>()
        while (cursor.moveToNext()) {
            result.add(
                ColumnInfo(
                    name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
                    type = cursor.getString(cursor.getColumnIndexOrThrow("type")),
                    notNull = cursor.getInt(cursor.getColumnIndexOrThrow("notnull")),
                ),
            )
        }
        cursor.close()
        return result
    }

    private data class ColumnInfo(
        val name: String,
        val type: String,
        val notNull: Int,
    )
}
