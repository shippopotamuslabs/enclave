package com.browntowndev.enclave.core.data.remote

import com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncAdapterRegistry
import com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncContext
import com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncDependencyGraph
import com.browntowndev.enclave.core.data.remote.testFirestoreSyncDaos
import com.browntowndev.enclave.core.data.repository.FakeLocalDataEncryptionService
import com.browntowndev.enclave.core.data.security.SensitivePersistenceSelfHealer
import com.browntowndev.enclave.core.data.security.StaticSensitiveFieldCatalog
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.security.CryptoManagerPort
import com.google.firebase.firestore.FirebaseFirestore
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Cross-table contract: [StaticSensitiveFieldCatalog], [FirestoreSensitiveFieldRefMapping],
 * [FirestoreSyncDependencyGraph], and [FirestoreSyncAdapterRegistry] must agree on
 * Firestore collection paths and Room entity owners.
 */
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
        val loggingPort = mockk<LoggingPort>(relaxed = true)
        return FirestoreSyncContext(
            firestore = mockk<FirebaseFirestore>(relaxed = true),
            daos = testFirestoreSyncDaos(),
            localEncryption = localEncryption,
            sensitiveSelfHealer = SensitivePersistenceSelfHealer(localEncryption, loggingPort),
            firestoreSensitiveFieldRepairer = mockk(relaxed = true),
            cryptoManager = mockk<CryptoManagerPort>(relaxed = true),
            loggingPort = loggingPort,
            apiKeyManager = mockk(relaxed = true),
            embeddingCoordinator = mockk(relaxed = true),
            userIdProvider = { "alice" },
            purgeInProgress = AtomicBoolean(false),
        )
    }
}
