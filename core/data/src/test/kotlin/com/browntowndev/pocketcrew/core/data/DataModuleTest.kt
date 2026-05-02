package com.browntowndev.pocketcrew.core.data

import android.content.Context
import androidx.room.DatabaseConfiguration
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.pocketcrew.core.data.local.PocketCrewDatabase
import com.browntowndev.pocketcrew.core.data.local.SQLiteVecInstaller
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DataModuleTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        mockkObject(SQLiteVecInstaller)
        
        // Mock the factory creation to avoid native library loading issues in Robolectric
        val mockFactory = mockk<SupportSQLiteOpenHelper.Factory>(relaxed = true)
        every { SQLiteVecInstaller.createOpenHelperFactory(any()) } returns mockFactory
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `providePocketCrewDatabase configures sqlite-vec open helper factory`() {
        // ACT
        val database = DataModule.providePocketCrewDatabase(context)
        
        // ASSERT
        verify { SQLiteVecInstaller.createOpenHelperFactory(context) }
        database.close()
    }

    @Test
    fun `providePocketCrewDatabase registers callback for virtual table creation`() {
        // ACT
        val database = DataModule.providePocketCrewDatabase(context)

        // ASSERT
        var current: Class<*>? = database.javaClass
        var configField: java.lang.reflect.Field? = null
        while (current != null && configField == null) {
            try {
                configField = current.getDeclaredField("mConfiguration")
            } catch (e: NoSuchFieldException) {
                current = current.superclass
            }
        }
        
        if (configField == null) {
            // Try without the 'm' prefix
            current = database.javaClass
            while (current != null && configField == null) {
                try {
                    configField = current.getDeclaredField("configuration")
                } catch (e: NoSuchFieldException) {
                    current = current.superclass
                }
            }
        }

        assertNotNull("Could not find configuration field in RoomDatabase hierarchy", configField)
        configField!!.isAccessible = true
        val config = configField.get(database) as? DatabaseConfiguration
        
        val callbacks = config?.callbacks
        assertTrue("Database should have at least one callback registered in configuration", !callbacks.isNullOrEmpty())
        database.close()
    }
}
