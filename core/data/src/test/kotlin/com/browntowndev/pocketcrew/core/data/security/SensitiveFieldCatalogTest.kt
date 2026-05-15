package com.browntowndev.pocketcrew.core.data.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SensitiveFieldCatalogTest {
    private val catalog = StaticSensitiveFieldCatalog()

    @Test
    fun allRefs_containsEveryAuditedSensitiveField() {
        val fieldsByOwner = catalog.allRefs().groupBy({ it.owner }, { it.field })

        assertEquals(setOf("name"), fieldsByOwner.getValue("ChatEntity").toSet())
        assertTrue(fieldsByOwner.getValue("MessageEntity").containsAll(messageSensitiveFields))
        assertTrue(fieldsByOwner.getValue("DocumentEntity").containsAll(setOf("json", "title", "author")))
        assertTrue(fieldsByOwner.getValue("StudioMediaEntity").containsAll(studioMediaSensitiveFields))
        assertTrue(fieldsByOwner.getValue("ApiCredentialsEntity").containsAll(setOf("displayName", "baseUrl", "encryptedApiKey")))
        assertTrue(fieldsByOwner.getValue("SettingsRepositoryImpl").contains("settings_custom_prompt_text"))
        assertTrue(fieldsByOwner.getValue("PipelineStateRepositoryImpl").contains("pipeline_state_chatId"))
        assertTrue(fieldsByOwner.getValue("ApiKeyManager").contains("api_key_value"))
    }

    @Test
    fun auditedOwners_includesSensitiveAndZeroSensitivePersistenceOwners() {
        val owners = catalog.auditedOwners()

        assertTrue(owners.contains("DefaultModelEntity"))
        assertTrue(owners.contains("LocalModelEntity"))
        assertTrue(owners.contains("EmbeddingEntity"))
        assertTrue(owners.contains("LocalVaultMetadataStore"))
        assertTrue(owners.contains("AndroidBiometricManager"))
        assertTrue(owners.contains("DownloadSessionManager"))
        assertTrue(owners.contains("Firestore.vault_metadata"))
    }

    @Test
    fun firestoreFields_returnsOnlySensitiveRemoteFields() {
        assertEquals(setOf("json", "title", "author"), catalog.firestoreFields("documents"))
        assertTrue(catalog.firestoreFields("messages").contains("content"))
        assertTrue("generated_media_types_json" !in catalog.firestoreFields("messages"))
    }

    private companion object {
        private val messageSensitiveFields =
            setOf(
                "content",
                "imageUrisJson",
                "thinkingRaw",
                "artifactsJson",
                "generatedMediaUrisJson",
                "generatedMediaAssetIdsJson",
                "generatedMediaPromptsJson",
            )

        private val studioMediaSensitiveFields =
            setOf("prompt", "mediaUri", "remoteUrl", "remoteThumbnailUrl", "stagingRemoteUrl")
    }
}
