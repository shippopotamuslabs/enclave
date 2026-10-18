@file:Suppress("ForbiddenMockKInTests")

// MockK retained only for the Firebase Firestore SDK surface (DocumentSnapshot), which has no JVM fake.

package com.browntowndev.enclave.core.data.remote.firestore.handlers

import com.browntowndev.enclave.core.data.local.ApiModelConfigurationEntity
import com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncContext
import com.browntowndev.enclave.core.data.remote.firestore.testSyncContext
import com.browntowndev.enclave.domain.model.config.ApiCredentialsId
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class ApiModelConfigSyncHandlerTest {
    @Test
    fun `SCN-SYNC-002 Preset Firestore Payload Includes Model`() =
        runTest {
            val handler = ApiModelConfigSyncHandler(createContext())
            val entity = configurationEntity(modelId = "claude-sonnet-4-20250514", isMultimodal = false)

            val payload = handler.toFirestore(entity)

            assertEquals("claude-sonnet-4-20250514", payload["model_id"])
            assertEquals(false, payload["is_multimodal"])
        }

    @Test
    fun `SCN-SYNC-004 Preset fromFirestore Restores Model Fields`() {
        val handler = ApiModelConfigSyncHandler(createContext())
        val doc =
            mockPresetDocument(
                entityId = "preset-1",
                data =
                    mapOf(
                        "api_credentials_id" to "cred-1",
                        "display_name" to "Preset",
                        "model_id" to "grok-4",
                        "is_multimodal" to true,
                        "updated_at" to 0L,
                        "deleted" to false,
                        "custom_headers" to "{}",
                        "max_tokens" to 4096L,
                        "context_window" to 4096L,
                        "temperature" to 0.7,
                        "top_p" to 0.95,
                        "system_prompt" to "",
                        "max_tokens_disabled" to false,
                        "temperature_disabled" to false,
                        "top_p_disabled" to false,
                        "top_k_disabled" to false,
                        "min_p_disabled" to false,
                        "frequency_penalty_disabled" to false,
                        "presence_penalty_disabled" to false,
                    ),
            )

        val entity = handler.fromFirestore(doc)

        assertEquals("grok-4", entity.modelId)
        assertEquals(true, entity.isMultimodal)
    }

    private fun configurationEntity(
        modelId: String,
        isMultimodal: Boolean,
    ): ApiModelConfigurationEntity =
        ApiModelConfigurationEntity(
            id = ApiModelConfigurationId("preset-1"),
            apiCredentialsId = ApiCredentialsId("cred-1"),
            displayName = "Preset",
            modelId = modelId,
            isMultimodal = isMultimodal,
        )

    private fun mockPresetDocument(
        entityId: String,
        data: Map<String, Any?>,
    ): DocumentSnapshot {
        val parent = mockk<CollectionReference>(relaxed = true)
        every { parent.id } returns "api_model_configs"
        val reference = mockk<DocumentReference>(relaxed = true)
        every { reference.parent } returns parent
        val doc = mockk<DocumentSnapshot>(relaxed = true)
        every { doc.id } returns entityId
        every { doc.reference } returns reference
        data.forEach { (field, value) ->
            when (value) {
                is String -> every { doc.getString(field) } returns value
                is Long -> every { doc.getLong(field) } returns value
                is Int -> every { doc.getLong(field) } returns value.toLong()
                is Double -> every { doc.getDouble(field) } returns value
                is Boolean -> every { doc.getBoolean(field) } returns value
            }
        }
        every { doc.getLong("top_k") } returns null
        every { doc.getDouble("min_p") } returns 0.05
        every { doc.getDouble("frequency_penalty") } returns 0.0
        every { doc.getDouble("presence_penalty") } returns 0.0
        return doc
    }

    private fun createContext(): FirestoreSyncContext = testSyncContext()
}
