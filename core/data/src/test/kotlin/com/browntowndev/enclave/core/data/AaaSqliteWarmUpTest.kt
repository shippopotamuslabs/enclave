package com.browntowndev.enclave.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.enclave.core.data.local.EnclaveDatabase
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * Cheap SQLite warm-up (ENC-253): named to sort first in :core:data so a
 * first-load native SQLite failure lands on this single-method class instead
 * of poisoning a whole feature test class sharing the Robolectric sandbox.
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class AaaSqliteWarmUpTest {
    @Test
    fun `warm up opens in-memory SQLite database`() {
        val database =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    EnclaveDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
        try {
            assertTrue(database.openHelper.writableDatabase.isOpen)
        } finally {
            database.close()
        }
    }
}
