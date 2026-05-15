package com.browntowndev.pocketcrew.core.data.remote

import com.browntowndev.pocketcrew.core.data.security.LocalDataEncryptionService
import com.browntowndev.pocketcrew.core.data.security.SensitivePersistenceSelfHealer
import com.browntowndev.pocketcrew.core.data.security.SensitiveRepairAction
import com.browntowndev.pocketcrew.core.data.security.StaticSensitiveFieldCatalog
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FirestoreSensitiveFieldRepairerTest {
    private val repairer =
        FirestoreSensitiveFieldRepairer(
            catalog = StaticSensitiveFieldCatalog(),
            healer =
                SensitivePersistenceSelfHealer(
                    localEncryption = FakeLocalDataEncryptionService(),
                    loggingPort = mockk<LoggingPort>(relaxed = true),
                ),
        )

    @Test
    fun repairOutbound_messagesUsesMessageEntityOwnerInRepairMetadata() {
        val fresh =
            FirestoreSensitiveFieldRepairer(
                catalog = StaticSensitiveFieldCatalog(),
                healer =
                    SensitivePersistenceSelfHealer(
                        localEncryption = FakeLocalDataEncryptionService(),
                        loggingPort = mockk<LoggingPort>(relaxed = true),
                    ),
            )
        fresh.repairOutbound(
            collectionPath = "messages",
            entityId = "msg-1",
            data = mapOf("content" to "hello"),
        )
        assertTrue(fresh.sanitizedEvents().any { it.ref.owner == "MessageEntity" && it.ref.field == "content" })
    }

    @Test
    fun repairOutbound_encryptsSensitiveFieldsOnly() {
        val repaired =
            repairer.repairOutbound(
                collectionPath = "messages",
                entityId = "msg-1",
                data =
                    mapOf(
                        "content" to "hello",
                        "generated_media_types_json" to "[\"IMAGE\"]",
                    ),
            )

        assertEquals("PCENC:v1:aes-gcm:hello", repaired["content"])
        assertEquals("[\"IMAGE\"]", repaired["generated_media_types_json"])
    }

    @Test
    fun repairInbound_encryptsPlaintextRemoteFieldsBeforeRoomInsert() {
        val repaired =
            repairer.repairInbound(
                collectionPath = "documents",
                entityId = "doc-1",
                data = mapOf("json" to "{\"blocks\":[]}", "title" to "Plain Title", "message_id" to "msg-1"),
            )

        assertEquals("PCENC:v1:aes-gcm:{\"blocks\":[]}", repaired["json"])
        assertEquals("PCENC:v1:aes-gcm:Plain Title", repaired["title"])
        assertEquals("msg-1", repaired["message_id"])
        assertTrue(repairer.sanitizedEvents().any { it.action == SensitiveRepairAction.REPAIRED })
    }

    @Test
    fun repairInbound_rejectsPlaintextApiKeyFields() {
        val repaired =
            repairer.repairInbound(
                collectionPath = "api_keys",
                entityId = "x_search",
                data = mapOf("api_key" to "sk-live", "alias" to "x_search"),
            )

        assertFalse(repaired.containsKey("api_key"))
        assertEquals("x_search", repaired["alias"])
        assertTrue(repairer.sanitizedEvents().any { it.action == SensitiveRepairAction.REJECTED_UNSUPPORTED_FIELD })
    }

    // T7: Firestore repairer produces distinct AAD per doc
    @Test
    fun repairOutbound_differentDocIds_producesDistinctAadPerDoc() {
        val aadRecordingFake = FakeLocalDataEncryptionService()
        val aadRecordingRepairer = FirestoreSensitiveFieldRepairer(
            catalog = StaticSensitiveFieldCatalog(),
            healer = SensitivePersistenceSelfHealer(
                localEncryption = aadRecordingFake,
                loggingPort = mockk<LoggingPort>(relaxed = true),
            ),
        )

        aadRecordingRepairer.repairOutbound(
            collectionPath = "documents",
            entityId = "doc-a",
            data = mapOf("json" to "x"),
        )
        aadRecordingRepairer.repairOutbound(
            collectionPath = "documents",
            entityId = "doc-b",
            data = mapOf("json" to "x"),
        )

        assertTrue(aadRecordingFake.encryptedAads.size >= 2)
        val first = aadRecordingFake.encryptedAads[0].toString(Charsets.UTF_8)
        val second = aadRecordingFake.encryptedAads[1].toString(Charsets.UTF_8)
        assertEquals("DocumentEntity|json|doc-a", first)
        assertEquals("DocumentEntity|json|doc-b", second)
    }

    private class FakeLocalDataEncryptionService : LocalDataEncryptionService {
        val encryptedAads = mutableListOf<ByteArray>()

        override fun encryptString(plaintext: String): String = "PCENC:v1:aes-gcm:$plaintext"

        override fun decryptString(stored: String): String = stored.removePrefix("PCENC:v1:aes-gcm:")

        override fun decryptString(stored: String, aad: ByteArray): String = stored.removePrefix("PCENC:v1:aes-gcm:")

        override fun isEncrypted(stored: String): Boolean = stored.startsWith("PCENC:v1:aes-gcm:")

        override fun encryptString(plaintext: String, aad: ByteArray): String {
            encryptedAads.add(aad)
            return encryptString(plaintext)
        }
    }
}
