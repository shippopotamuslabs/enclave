package com.browntowndev.pocketcrew.core.data.security

import com.browntowndev.pocketcrew.core.data.security.DecryptFailedException
import com.browntowndev.pocketcrew.core.data.security.MalformedEnvelopeException
import com.browntowndev.pocketcrew.core.data.security.MalformedEnvelopeReason
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SensitivePersistenceSelfHealerTest {
    private val loggingPort = mockk<LoggingPort>(relaxed = true)

    @Test
    fun repairString_plaintextAndUnlocked_returnsEncryptedEnvelopeAndSanitizedWarning() {
        val messageSlot = slot<String>()
        every { loggingPort.warning(any(), capture(messageSlot)) } returns Unit
        val healer = SensitivePersistenceSelfHealer(FakeLocalDataEncryptionService(), loggingPort)

        val result = healer.repairString(messageContentRef, "msg-1", "secret text")

        assertEquals(SensitiveRepairAction.REPAIRED, result.action)
        assertEquals("PCENC:v1:aes-gcm:secret text", result.value)
        assertTrue(messageSlot.captured.contains("MessageEntity"))
        assertTrue(messageSlot.captured.contains("content"))
        assertFalse(messageSlot.captured.contains("secret text"))
        assertFalse(messageSlot.captured.contains("PCENC:"))
    }

    @Test
    fun repairString_lockedVault_defersAndLeavesValueUnchanged() {
        val healer = SensitivePersistenceSelfHealer(LockedLocalDataEncryptionService(), loggingPort)

        val result = healer.repairString(messageContentRef, "msg-1", "secret text")

        assertEquals(SensitiveRepairAction.DEFERRED_LOCKED, result.action)
        assertEquals("secret text", result.value)
        verify { loggingPort.warning(any(), match { it.contains("DEFERRED_LOCKED") }) }
    }

    @Test
    fun decryptForRead_plaintextUnlocked_queuesRoomReadRepairAndReturnsPlaintext() {
        val healer = SensitivePersistenceSelfHealer(FakeLocalDataEncryptionService(), loggingPort)
        val repairs = mutableListOf<SensitiveRoomReadRepair>()
        val result = healer.decryptForRead(messageContentRef, "msg-1", "hello", readRepairs = repairs)
        assertEquals("hello", result)
        assertEquals(1, repairs.size)
        assertEquals("PCENC:v1:aes-gcm:hello", repairs.single().encryptedValue)
        assertEquals("msg-1", repairs.single().entityId)
        assertEquals("content", repairs.single().ref.field)
    }

    @Test
    fun decryptForRead_invalidCiphertext_returnsNullAndLogsMetadataOnly() {
        val messageSlot = slot<String>()
        every { loggingPort.warning(any(), capture(messageSlot)) } returns Unit
        val healer = SensitivePersistenceSelfHealer(ThrowingDecryptEncryptionService(), loggingPort)

        val result = healer.decryptForRead(messageContentRef, "msg-1", "PCENC:v1:aes-gcm:not-valid")

        assertNull(result)
        assertTrue(messageSlot.captured.contains("REJECTED_INVALID_CIPHERTEXT"))
        assertFalse(messageSlot.captured.contains("not-valid"))
    }

    @Test
    fun decryptForRead_plaintextValue_vaultUnlocked_returnsPlaintext() {
        val healer = SensitivePersistenceSelfHealer(FakeLocalDataEncryptionService(), loggingPort)
        val result = healer.decryptForRead(
            SensitiveFieldRef("MessageEntity", "content", PersistenceBoundary.ROOM),
            "m1",
            "hello"
        )
        assertEquals("hello", result)
    }

    @Test
    fun decryptForRead_malformedEnvelope_emitsRejectedMalformedEnvelopeAction() {
        val messageSlot = slot<String>()
        every { loggingPort.warning(any(), capture(messageSlot)) } returns Unit
        val healer = SensitivePersistenceSelfHealer(MalformedThrowingEncryptionService(), loggingPort)

        val result = healer.decryptForRead(
            SensitiveFieldRef("MessageEntity", "content", PersistenceBoundary.ROOM),
            "m1",
            "PCENC:v1:aes-gcm:@@@@"
        )

        assertNull(result)
        assertTrue(messageSlot.captured.contains("REJECTED_MALFORMED_ENVELOPE"))
    }

    @Test
    fun decryptForRead_decryptFailed_emitsRejectedInvalidCiphertextAction() {
        val messageSlot = slot<String>()
        every { loggingPort.warning(any(), capture(messageSlot)) } returns Unit
        val healer = SensitivePersistenceSelfHealer(DecryptFailedThrowingEncryptionService(), loggingPort)

        val result = healer.decryptForRead(
            SensitiveFieldRef("MessageEntity", "content", PersistenceBoundary.ROOM),
            "m1",
            "PCENC:v1:aes-gcm:unused"
        )

        assertNull(result)
        assertTrue(messageSlot.captured.contains("REJECTED_INVALID_CIPHERTEXT"))
    }

    private companion object {
        private val messageContentRef = SensitiveFieldRef("MessageEntity", "content", PersistenceBoundary.ROOM)
    }

    private class FakeLocalDataEncryptionService : LocalDataEncryptionService {
        override fun encryptString(plaintext: String): String = "PCENC:v1:aes-gcm:$plaintext"

        override fun decryptString(stored: String): String = stored.removePrefix("PCENC:v1:aes-gcm:")

        override fun isEncrypted(stored: String): Boolean = stored.startsWith("PCENC:v1:aes-gcm:")
    }

    private class LockedLocalDataEncryptionService : LocalDataEncryptionService {
        override fun encryptString(plaintext: String): String = error("locked")

        override fun decryptString(stored: String): String = error("locked")

        override fun isEncrypted(stored: String): Boolean = stored.startsWith("PCENC:v1:aes-gcm:")

        override fun isUnlocked(): Boolean = false
    }

    private class ThrowingDecryptEncryptionService : LocalDataEncryptionService {
        override fun encryptString(plaintext: String): String = "PCENC:v1:aes-gcm:$plaintext"

        override fun decryptString(stored: String): String = throw IllegalArgumentException("bad ciphertext")

        override fun isEncrypted(stored: String): Boolean = stored.startsWith("PCENC:v1:aes-gcm:")
    }

    private class MalformedThrowingEncryptionService : LocalDataEncryptionService {
        override fun encryptString(plaintext: String): String = "PCENC:v1:aes-gcm:$plaintext"

        override fun decryptString(stored: String): String =
            throw MalformedEnvelopeException(MalformedEnvelopeReason.INVALID_BASE64)

        override fun isEncrypted(stored: String): Boolean = stored.startsWith("PCENC:v1:aes-gcm:")
    }

    private class DecryptFailedThrowingEncryptionService : LocalDataEncryptionService {
        override fun encryptString(plaintext: String): String = "PCENC:v1:aes-gcm:$plaintext"

        override fun decryptString(stored: String): String =
            throw DecryptFailedException("tag mismatch", null)

        override fun isEncrypted(stored: String): Boolean = stored.startsWith("PCENC:v1:aes-gcm:")
    }

    private class AadRecordingDecryptEncryptionService : LocalDataEncryptionService {
        var lastDecryptAad: ByteArray? = null

        override fun encryptString(plaintext: String): String = "PCENC:v1:aes-gcm:$plaintext"

        override fun decryptString(stored: String): String {
            return stored.removePrefix("PCENC:v1:aes-gcm:")
        }

        override fun isEncrypted(stored: String): Boolean = stored.startsWith("PCENC:v1:aes-gcm:")

        fun decryptString(stored: String, aad: ByteArray): String {
            lastDecryptAad = aad
            return decryptString(stored)
        }
    }

    // T8: decryptForRead passes entityId-based AAD to LocalDataEncryptionService
    @Test
    fun decryptForRead_encryptedValue_passesEntityScopedAadToDecryptService() {
        val aadFake = AadRecordingDecryptEncryptionService()
        val healer = SensitivePersistenceSelfHealer(aadFake, loggingPort)
        val stored = "PCENC:v1:aes-gcm:body"

        healer.decryptForRead(
            SensitiveFieldRef("MessageEntity", "content", PersistenceBoundary.ROOM),
            "msg-99",
            stored
        )

        val aadUtf8 = aadFake.lastDecryptAad?.toString(Charsets.UTF_8)
        assertEquals("MessageEntity|content|msg-99", aadUtf8)
    }

    // T8 happy path: decryptForRead with matching AAD returns decrypted value
    @Test
    fun decryptForRead_encryptedValueMatchingAad_returnsDecryptedContent() {
        val aadFake = AadRecordingDecryptEncryptionService()
        val healer = SensitivePersistenceSelfHealer(aadFake, loggingPort)
        val stored = "PCENC:v1:aes-gcm:body"

        val result = healer.decryptForRead(
            SensitiveFieldRef("MessageEntity", "content", PersistenceBoundary.ROOM),
            "msg-99",
            stored
        )

        assertEquals("body", result)
    }
}
