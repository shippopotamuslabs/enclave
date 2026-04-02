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
    fun `database exposes version 3 tables and omits legacy ones`() {
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
        assertFalse(hasTable("models"))
        assertFalse(hasTable("api_models"))

        val cursor = sqliteDb.query("SELECT COUNT(*) FROM local_models")
        cursor.moveToFirst()
        assertEquals(0, cursor.getInt(0))
        cursor.close()

        db.close()
    }
}
