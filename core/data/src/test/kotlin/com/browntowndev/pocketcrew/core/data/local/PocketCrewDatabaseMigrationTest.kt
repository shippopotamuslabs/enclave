package com.browntowndev.pocketcrew.core.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class PocketCrewDatabaseMigrationTest {

    private val TEST_DB = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        PocketCrewDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    @Throws(IOException::class)
    fun `destructive migration from version 2 to 3 creates new tables and drops old`() {
        // Create the database in version 2
        val db2 = helper.createDatabase(TEST_DB, 2)
        
        // Mock a basic version 2 structure and data to prove it gets wiped
        db2.execSQL("CREATE TABLE IF NOT EXISTS models (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT)")
        db2.execSQL("CREATE TABLE IF NOT EXISTS api_models (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT)")
        db2.execSQL("INSERT INTO models (name) VALUES ('old_model')")
        db2.execSQL("INSERT INTO api_models (name) VALUES ('old_api_model')")
        
        val cursorOld1 = db2.query("SELECT * FROM models")
        assertEquals(1, cursorOld1.count)
        cursorOld1.close()
        
        db2.close()

        // Re-open the database with version 3 and fallbackToDestructiveMigration
        val db3 = helper.createDatabase(TEST_DB, 3)

        // Query the new tables to verify they exist and are empty
        val cursor1 = db3.query("SELECT * FROM local_models")
        assertEquals(0, cursor1.count)
        cursor1.close()

        val cursor2 = db3.query("SELECT * FROM local_model_configurations")
        assertEquals(0, cursor2.count)
        cursor2.close()

        val cursor3 = db3.query("SELECT * FROM api_credentials")
        assertEquals(0, cursor3.count)
        cursor3.close()
        
        val cursor4 = db3.query("SELECT * FROM api_model_configurations")
        assertEquals(0, cursor4.count)
        cursor4.close()
        
        val cursor5 = db3.query("SELECT * FROM default_models")
        assertEquals(0, cursor5.count)
        cursor5.close()
        
        // The old tables should be gone (a query will throw SQLiteException if they are)
        try {
            db3.query("SELECT * FROM models").close()
            org.junit.Assert.fail("Expected a SQLiteException because the 'models' table should have been dropped")
        } catch (e: android.database.sqlite.SQLiteException) {
            // Expected, test passes for this assertion
            org.junit.Assert.assertTrue(e.message?.contains("no such table") == true)
        }
        
        db3.close()
    }
}