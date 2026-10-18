@file:Suppress("ForbiddenMockKInTests")
// MockK is used only for com.google.firebase.firestore.FirebaseFirestore, a Firebase SDK class with no hand-writable fake (ENC-28).

package com.browntowndev.enclave.core.data.remote

import com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncAdapterRegistry
import com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncContext
import com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncDependencyGraph
import com.browntowndev.enclave.core.data.remote.testFirestoreSyncDaos
import com.browntowndev.enclave.core.data.repository.FakeLocalDataEncryptionService
import com.browntowndev.enclave.core.data.security.SensitivePersistenceSelfHealer
import com.browntowndev.enclave.core.data.security.StaticSensitiveFieldCatalog
import com.browntowndev.enclave.core.data.FakeApiKeyManager
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.enclave.core.data.testWorkManager
import com.browntowndev.enclave.core.data.sync.EmbeddingRegenerationCoordinator
import com.browntowndev.enclave.core.testing.FakeCryptoManagerPort
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.google.firebase.firestore.FirebaseFirestore
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Cross-table contract: [StaticSensitiveFieldCatalog], [FirestoreSensitiveFieldRefMapping],
 * [FirestoreSyncDependencyGraph], and [FirestoreSyncAdapterRegistry] must agree on
 * Firestore collection paths and Room entity owners.
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class FirestoreSensitiveFieldRefMappingContractTest {
    private val catalog = StaticSensitiveFieldCatalog()

    @Test
    @DisplayName("SCN-SYNC-006")
    fun scnSync006_catalogFirestoreCollectionsMapToRoomOwners() {
        val roomOwners = catalog.allRefs().map { it.owner }.toSet()
        val collectionsWithSensitiveFields =
            registryCollectionPaths()
                .filter { catalog.firestoreFields(it).isNotEmpty() }

        assertTrue(collectionsWithSensitiveFields.isNotEmpty())
        for (collectionPath in collectionsWithSensitiveFields) {
            val firestoreField = catalog.firestoreFields(collectionPath).first()
            val ref = FirestoreSensitiveFieldRefMapping.sensitiveFieldRef(collectionPath, firestoreField)
            assertNotEquals(
                collectionPath,
                ref.owner,
                "Collection $collectionPath must not fall back to collection path as AAD owner",
            )
            assertTrue(
                ref.owner in roomOwners,
                "Collection $collectionPath must map to a catalog Room owner, got ${ref.owner}",
            )
        }
    }

    @Test
    fun liveVoiceProvidersMapsToLiveVoiceProviderEntity() {
        val ref =
            FirestoreSensitiveFieldRefMapping.sensitiveFieldRef(
                collectionPath = "live_voice_providers",
                firestoreSnakeField = "display_name",
            )
        assertEquals("LiveVoiceProviderEntity", ref.owner)
        assertEquals("displayName", ref.field)
    }

    @Test
    fun registryAndDependencyGraphAgreeOnCollectionPaths() {
        val registryPaths = registryCollectionPaths()
        val graphPaths = FirestoreSyncDependencyGraph.collectionPaths()

        assertEquals(0, registryPaths.minus(graphPaths).size, "Registry paths missing from graph")
        assertEquals(0, graphPaths.minus(registryPaths).size, "Graph paths missing from registry")
    }

    private fun registryCollectionPaths(): Set<String> =
        FirestoreSyncAdapterRegistry(createMinimalContext())
            .adapters
            .map { it.collectionPath }
            .toSet()

    private fun createMinimalContext(): FirestoreSyncContext {
        val localEncryption = FakeLocalDataEncryptionService()
        val loggingPort = FakeLoggingPort()
        val healer = SensitivePersistenceSelfHealer(localEncryption, loggingPort)
        return FirestoreSyncContext(
            firestore = mockk<FirebaseFirestore>(relaxed = true),
            daos = testFirestoreSyncDaos(),
            localEncryption = localEncryption,
            sensitiveSelfHealer = healer,
            firestoreSensitiveFieldRepairer =
                FirestoreSensitiveFieldRepairer(
                    catalog = StaticSensitiveFieldCatalog(),
                    healer = healer,
                ),
            cryptoManager = FakeCryptoManagerPort(),
            loggingPort = loggingPort,
            apiKeyManager = FakeApiKeyManager(),
            embeddingCoordinator =
                EmbeddingRegenerationCoordinator(
                    testWorkManager(ApplicationProvider.getApplicationContext()),
                    CoroutineScope(SupervisorJob() + Dispatchers.Default),
                ),
            userIdProvider = { "alice" },
            purgeInProgress = AtomicBoolean(false),
        )
    }
}
