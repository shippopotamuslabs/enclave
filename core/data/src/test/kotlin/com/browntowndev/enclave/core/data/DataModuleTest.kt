package com.browntowndev.enclave.core.data

import android.content.Context
import androidx.room.DatabaseConfiguration
import androidx.test.core.app.ApplicationProvider
import io.requery.android.database.sqlite.RequerySQLiteOpenHelperFactory
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.io.File

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class DataModuleTest {
    private lateinit var context: Context

    @BeforeEach
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        installFakeNativeLibraryDir()
    }

    /**
     * SQLiteVecInstaller resolves the sqlite-vec extension path eagerly when
     * DataModule creates the open-helper factory: it first checks for
     * libsqlite_vec.so in the application's native library directory and only
     * falls back to scanning the installed APK when the file is absent. A real
     * install always has the library there; Robolectric's does not, which made
     * the provider throw "Unable to find libsqlite_vec.so in installed APK
     * sources" before Room even built the database. Point nativeLibraryDir at
     * a scratch directory holding the file so the real installer runs end to
     * end. The bytes are never read on this path (only existence is checked)
     * and never loaded: these tests build the database but never open it.
     */
    private fun installFakeNativeLibraryDir() {
        val nativeLibDir = File(System.getProperty("java.io.tmpdir"), "sqlite-vec-native-lib")
        nativeLibDir.mkdirs()
        File(nativeLibDir, "libsqlite_vec.so").writeBytes(ByteArray(0))
        context.applicationInfo.nativeLibraryDir = nativeLibDir.absolutePath
    }

    /** Reads the Room [DatabaseConfiguration] the built database carries, via the same reflection walk as before. */
    private fun configurationOf(database: com.browntowndev.enclave.core.data.local.EnclaveDatabase): DatabaseConfiguration? {
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

        assertNotNull(configField, "Could not find configuration field in RoomDatabase hierarchy")
        configField!!.isAccessible = true
        return configField.get(database) as? DatabaseConfiguration
    }

    @Test
    fun `provideEnclaveDatabase configures sqlite-vec open helper factory`() {
        // ACT — the real SQLiteVecInstaller factory is used; building (not opening)
        // the database resolves the extension path but never loads the native code.
        val database = DataModule.provideEnclaveDatabase(context)

        // ASSERT: the configuration carries the installer's Requery factory.
        val config = configurationOf(database)
        assertTrue(
            config?.sqliteOpenHelperFactory is RequerySQLiteOpenHelperFactory,
            "Expected SQLiteVecInstaller's RequerySQLiteOpenHelperFactory, got ${config?.sqliteOpenHelperFactory}",
        )
        database.close()
    }

    @Test
    fun `provideEnclaveDatabase registers callback for virtual table creation`() {
        // ACT
        val database = DataModule.provideEnclaveDatabase(context)

        // ASSERT
        val config = configurationOf(database)

        val callbacks = config?.callbacks
        assertTrue(!callbacks.isNullOrEmpty(), "Database should have at least one callback registered in configuration")
        database.close()
    }
}
