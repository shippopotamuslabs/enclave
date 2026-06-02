package com.browntowndev.enclave.core.data.remote.firestore

import com.browntowndev.enclave.core.data.remote.EntitySyncAdapter
import com.browntowndev.enclave.core.data.remote.testFirestoreSyncDaos
import com.browntowndev.enclave.core.data.repository.FakeLocalDataEncryptionService
import com.browntowndev.enclave.core.data.security.SensitivePersistenceSelfHealer
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

        // 22 = the production handler list after `SearchToolSettingsSyncHandler` is
        // registered (21 prior + 1 new). See `FirestoreSyncDependencyGraphTest` for the
        // matching assertion on the graph side.
        assertEquals(22, registry.adapters.size)
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

    @Test
    @DisplayName("S-HANDLER-001 registry registers the search_tool_settings handler")
    fun `S-HANDLER-001 registry registers a handler for the search_tool_settings collection`() {
        val ctx = createMinimalContext()
        val registry = FirestoreSyncAdapterRegistry(ctx)

        assertTrue(
            registry.adapters.any { it.collectionPath == "search_tool_settings" },
            "FirestoreSyncAdapterRegistry must register a handler for the search_tool_settings collection",
        )
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
