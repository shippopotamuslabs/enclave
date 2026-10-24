package com.browntowndev.enclave.core.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class FirebaseAvailabilityTest {
    private lateinit var context: Context

    @BeforeEach
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        clearFirebaseApps()
    }

    @AfterEach
    fun tearDown() {
        clearFirebaseApps()
    }

    private fun clearFirebaseApps() {
        FirebaseApp.getApps(context).toList().forEach { app ->
            runCatching { app.delete() }
        }
    }

    @Test
    fun `isConfigBacked is false when no Firebase app is installed`() {
        assertTrue(FirebaseApp.getApps(context).isEmpty())
        assertFalse(FirebaseAvailability.isConfigBacked(context))
    }

    @Test
    fun `ensureFirebaseApp installs placeholder and reports dark`() {
        val configBacked = FirebaseAvailability.ensureFirebaseApp(context)

        assertFalse(configBacked)
        assertFalse(FirebaseAvailability.isConfigBacked(context))
        assertTrue(FirebaseApp.getApps(context).isNotEmpty())
        assertEquals("enclave-dark-no-project", FirebaseApp.getInstance().options.projectId)
    }

    @Test
    fun `FirebaseAuth constructs signed-out after dark ensure`() {
        FirebaseAvailability.ensureFirebaseApp(context)

        val auth = DataModule.provideFirebaseAuth(context)

        assertNotNull(auth)
        assertNull(auth.currentUser)
    }

    @Test
    fun `DataModule Firebase providers construct in dark mode`() {
        assertNotNull(DataModule.provideFirebaseFirestore(context))
        assertNotNull(DataModule.provideFirebaseFunctions(context))
        assertNotNull(DataModule.provideFirebaseStorage(context))
    }

    @Test
    fun `ensureFirebaseApp is idempotent in dark mode`() {
        assertFalse(FirebaseAvailability.ensureFirebaseApp(context))
        assertFalse(FirebaseAvailability.ensureFirebaseApp(context))
        assertEquals(1, FirebaseApp.getApps(context).size)
    }
}
