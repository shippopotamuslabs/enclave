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

        // 23 = production total after `LiveVoiceProviderSyncHandler` is registered.
        assertEquals(23, registryPaths.size)
        assertEquals(0, registryPaths.minus(graphPaths).size)
        assertEquals(0, graphPaths.minus(registryPaths).size)
    }

    @Test
    fun `default_models parents use DefaultModelSyncHandler Firestore field names`() {
        val parents =
            FirestoreSyncDependencyGraph.requiredParents(
                collectionPath = "default_models",
                sourceData =
                    mapOf(
                        "local_config_id" to "local-preset-1",
                        "api_config_id" to "api-preset-1",
                        "tts_provider_id" to "tts-1",
                        "live_voice_provider_id" to "live-voice-1",
                        "media_provider_id" to "media-1",
                    ),
            )

        assertEquals(
            listOf(
                FirestoreSyncDependencyGraph.ParentRef("local_model_configs", "local-preset-1"),
                FirestoreSyncDependencyGraph.ParentRef("api_model_configs", "api-preset-1"),
                FirestoreSyncDependencyGraph.ParentRef("tts_providers", "tts-1"),
                FirestoreSyncDependencyGraph.ParentRef("live_voice_providers", "live-voice-1"),
                FirestoreSyncDependencyGraph.ParentRef("media_providers", "media-1"),
            ),
            parents,
        )
    }

    @Test
    fun `default_models parents accept legacy config id field aliases`() {
        val parents =
            FirestoreSyncDependencyGraph.requiredParents(
                collectionPath = "default_models",
                sourceData =
                    mapOf(
                        "local_model_config_id" to "legacy-local",
                        "api_model_config_id" to "legacy-api",
                    ),
            )

        assertEquals(
            listOf(
                FirestoreSyncDependencyGraph.ParentRef("local_model_configs", "legacy-local"),
                FirestoreSyncDependencyGraph.ParentRef("api_model_configs", "legacy-api"),
            ),
            parents,
        )
    }

    @Test
    @org.junit.jupiter.api.DisplayName("SCN-SYNC-004")
    fun scnSync004_dependencyGraphListsUtilityModelsWithNoParents() {
        val parents =
            FirestoreSyncDependencyGraph.requiredParents(
                collectionPath = "utility_models",
                sourceData = emptyMap(),
            )

        assertEquals(emptyList<FirestoreSyncDependencyGraph.ParentRef>(), parents)
        assertTrue("utility_models" in FirestoreSyncDependencyGraph.collectionPaths())
    }

    @Test
    @org.junit.jupiter.api.DisplayName("S-HANDLER-002")
    fun `S-HANDLER-002 dependency graph declares search_tool_settings with no parents`() {
        assertTrue(
            "search_tool_settings" in FirestoreSyncDependencyGraph.collectionPaths(),
            "FirestoreSyncDependencyGraph must list search_tool_settings as a known collection path",
        )

        val parents =
            FirestoreSyncDependencyGraph.requiredParents(
                collectionPath = "search_tool_settings",
                sourceData = mapOf("search_enabled" to true, "x_search_enabled" to false, "search_provider" to "EXA"),
            )
        assertEquals(
            emptyList<FirestoreSyncDependencyGraph.ParentRef>(),
            parents,
            "search_tool_settings must declare NO_PARENTS (the document is a singleton with no foreign-key parents)",
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
            userIdProvider = { "alice" },
            purgeInProgress = AtomicBoolean(false),
        )
    }
}
