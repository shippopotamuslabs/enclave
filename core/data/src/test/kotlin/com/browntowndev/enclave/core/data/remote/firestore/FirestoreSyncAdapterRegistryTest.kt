package com.browntowndev.enclave.core.data.remote.firestore

import com.browntowndev.enclave.core.data.remote.EntitySyncAdapter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class FirestoreSyncAdapterRegistryTest {
    @Test
    @DisplayName("registry exposes all entity sync handlers")
    fun registry_containsExpectedHandlers() {
        val ctx = createMinimalContext()
        val registry = FirestoreSyncAdapterRegistry(ctx)

        // 23 = production handlers after `LiveVoiceProviderSyncHandler` is registered.
        assertEquals(23, registry.adapters.size)
        assertTrue(registry.adapters.any { it.collectionPath == "live_voice_providers" })
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
    fun registry_uploadsDefaultModelParentsBeforeDefaultModels() {
        val ctx = createMinimalContext()
        val registry = FirestoreSyncAdapterRegistry(ctx)
        val paths = registry.adapters.map(EntitySyncAdapter<*>::collectionPath)
        val defaultModelsIndex = paths.indexOf("default_models")
        val parentPaths =
            listOf(
                "api_credentials",
                "local_models",
                "tts_providers",
                "live_voice_providers",
                "media_providers",
                "api_model_configs",
                "local_model_configs",
            )

        for (parentPath in parentPaths) {
            assertTrue(
                paths.indexOf(parentPath) in 0 until defaultModelsIndex,
                "$parentPath must upload before default_models but was at ${paths.indexOf(parentPath)}",
            )
        }
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

    private fun createMinimalContext(): FirestoreSyncContext = testSyncContext()
}
