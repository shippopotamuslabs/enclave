package com.browntowndev.enclave.core.data.remote.firestore

import com.browntowndev.enclave.core.data.remote.EntitySyncAdapter
import com.browntowndev.enclave.core.data.remote.testFirestoreSyncDaos
import com.browntowndev.enclave.core.data.remote.firestore.handlers.ChatSyncHandler
import com.browntowndev.enclave.core.data.repository.FakeLocalDataEncryptionService
import com.browntowndev.enclave.core.data.security.SensitivePersistenceSelfHealer
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.security.CryptoManagerPort
import com.google.firebase.firestore.FirebaseFirestore
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean

class FirestoreSyncAdapterRegistryTest {
    @Test
    @DisplayName("registry exposes all entity sync handlers")
    fun registry_containsExpectedHandlers() {
        val ctx = createMinimalContext()
        val registry = FirestoreSyncAdapterRegistry(ctx)

        assertEquals(19, registry.adapters.size)
        assertTrue(registry.adapters.any { it is ChatSyncHandler })
        assertTrue(registry.adapters.any { it.collectionPath == "memories" })
        assertTrue(registry.adapters.any { it.collectionPath == "api_keys" })
    }

    @Test
    @DisplayName("adapter collection paths are unique")
    fun registry_collectionPathsAreUnique() {
        val ctx = createMinimalContext()
        val registry = FirestoreSyncAdapterRegistry(ctx)
        val paths = registry.adapters.map(EntitySyncAdapter<*>::collectionPath)

        assertEquals(paths.size, paths.toSet().size)
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
            userIdProvider = { "user-1" },
            purgeInProgress = AtomicBoolean(false),
        )
    }
}
