package com.browntowndev.pocketcrew.core.data.remote.firestore

import com.browntowndev.pocketcrew.core.data.remote.EntitySyncAdapter
import com.browntowndev.pocketcrew.core.data.remote.testFirestoreSyncDaos
import com.browntowndev.pocketcrew.core.data.repository.FakeLocalDataEncryptionService
import com.browntowndev.pocketcrew.core.data.security.SensitivePersistenceSelfHealer
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.security.CryptoManagerPort
import com.google.firebase.firestore.FirebaseFirestore
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean

class FirestoreSyncDependencyGraphTest {
    @Test
    fun `SCN-ANDROID-008 every adapter collection has a dependency graph entry`() {
        val ctx = createMinimalContext()
        val registryPaths =
            FirestoreSyncAdapterRegistry(ctx)
                .adapters
                .map(EntitySyncAdapter<*>::collectionPath)
                .toSet()
        val graphPaths = FirestoreSyncDependencyGraph.collectionPaths()

        assertEquals(19, registryPaths.size)
        assertEquals(0, registryPaths.minus(graphPaths).size)
        assertEquals(0, graphPaths.minus(registryPaths).size)
    }

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
