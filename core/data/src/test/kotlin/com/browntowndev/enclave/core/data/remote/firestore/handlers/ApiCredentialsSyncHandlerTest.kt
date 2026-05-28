package com.browntowndev.enclave.core.data.remote.firestore.handlers

import com.browntowndev.enclave.core.data.local.ApiCredentialsEntity
import com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncContext
import com.browntowndev.enclave.core.data.remote.testFirestoreSyncDaos
import com.browntowndev.enclave.core.data.repository.FakeLocalDataEncryptionService
import com.browntowndev.enclave.core.data.security.SensitivePersistenceSelfHealer
import com.browntowndev.enclave.domain.model.config.ApiCredentialsId
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.security.CryptoManagerPort
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean

class ApiCredentialsSyncHandlerTest {
    @Test
    fun `SCN-SYNC-001 Credentials Firestore Payload Excludes Model`() =
        runTest {
            val handler = ApiCredentialsSyncHandler(createContext())
            val entity =
                ApiCredentialsEntity(
                    id = ApiCredentialsId("cred-1"),
                    displayName = "Test",
                    provider = ApiProvider.OPENAI,
                    credentialAlias = "openai-main",
                )

            val payload = handler.toFirestore(entity)

            assertFalse(payload.containsKey("model_id"))
            assertFalse(payload.containsKey("is_multimodal"))
        }

    @Test
    fun `SCN-SYNC-003 Credentials fromFirestore Ignores Legacy Model Fields`() {
        val handler = ApiCredentialsSyncHandler(createContext())
        val doc =
            mockCredentialsDocument(
                entityId = "cred-legacy",
                data =
                    mapOf(
                        "provider" to "OPENAI",
                        "credential_alias" to "openai-main",
                        "model_id" to "legacy-model",
                        "is_multimodal" to true,
                        "created_at" to 0L,
                        "updated_at" to 0L,
                        "deleted" to false,
                        "display_name" to "PCENC:v1:aes-gcm:Test",
                    ),
            )

        val entity = handler.fromFirestore(doc)

        assertEquals(ApiCredentialsId("cred-legacy"), entity.id)
        assertEquals("openai-main", entity.credentialAlias)
        assertEquals(ApiProvider.OPENAI, entity.provider)
    }

    private fun mockCredentialsDocument(
        entityId: String,
        data: Map<String, Any?>,
    ): DocumentSnapshot {
        val parent = mockk<CollectionReference>(relaxed = true)
        every { parent.id } returns "api_credentials"
        val reference = mockk<DocumentReference>(relaxed = true)
        every { reference.parent } returns parent
        val doc = mockk<DocumentSnapshot>(relaxed = true)
        every { doc.id } returns entityId
        every { doc.reference } returns reference
        data.forEach { (field, value) ->
            when (value) {
                is String -> every { doc.getString(field) } returns value
                is Long -> every { doc.getLong(field) } returns value
                is Boolean -> every { doc.getBoolean(field) } returns value
                null -> every { doc.getString(field) } returns null
            }
        }
        every { doc.getBlob("encrypted_api_key") } returns null
        every { doc.getString("api_key_signature") } returns null
        every { doc.getString("base_url") } returns null
        return doc
    }

    private fun createContext(): FirestoreSyncContext {
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
