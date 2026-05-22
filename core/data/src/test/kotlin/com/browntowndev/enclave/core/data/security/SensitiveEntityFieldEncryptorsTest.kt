package com.browntowndev.enclave.core.data.security

import com.browntowndev.enclave.core.data.local.ApiCredentialsEntity
import com.browntowndev.enclave.core.data.local.DocumentEntity
import com.browntowndev.enclave.core.data.local.MessageEntity
import com.browntowndev.enclave.core.data.local.StudioMediaEntity
import com.browntowndev.enclave.core.data.security.AadContext
import com.browntowndev.enclave.core.data.security.CryptoManagerImpl
import com.browntowndev.enclave.domain.model.artifact.MimeType
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.MessageState
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.config.ApiCredentialsId
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SensitiveEntityFieldEncryptorsTest {
    private val healer =
        SensitivePersistenceSelfHealer(
            localEncryption = FakeLocalDataEncryptionService(),
            loggingPort = mockk<LoggingPort>(relaxed = true),
        )

    @Test
    fun encrypt_messageEntity_encryptsSensitiveContentAndLeavesTypeMetadataPlaintext() {
        val entity =
            MessageEntity(
                id = MessageId("msg-1"),
                chatId = ChatId("chat-1"),
                content = "hello",
                imageUrisJson = "[\"file://a.png\"]",
                role = Role.USER,
                thinkingRaw = "private thought",
                artifactsJson = "{\"body\":\"secret\"}",
                generatedMediaUrisJson = "[\"file://media.png\"]",
                generatedMediaAssetIdsJson = "[\"asset-1\"]",
                generatedMediaTypesJson = "[\"IMAGE\"]",
                generatedMediaPromptsJson = "[\"draw home\"]",
                messageState = MessageState.COMPLETE,
            )

        val encrypted = SensitiveEntityFieldEncryptors.encrypt(entity, healer)

        assertEncrypted(encrypted.content)
        assertEncrypted(encrypted.imageUrisJson)
        assertEncrypted(encrypted.thinkingRaw)
        assertEncrypted(encrypted.artifactsJson)
        assertEncrypted(encrypted.generatedMediaUrisJson)
        assertEncrypted(encrypted.generatedMediaAssetIdsJson)
        assertEncrypted(encrypted.generatedMediaPromptsJson)
        assertEquals("[\"IMAGE\"]", encrypted.generatedMediaTypesJson)
    }

    @Test
    fun encrypt_documentEntity_encryptsJsonTitleAndAuthor() {
        val entity =
            DocumentEntity(
                id = "doc-1",
                messageId = MessageId("msg-1"),
                json = "{\"blocks\":[]}",
                title = "Report",
                author = "Sean",
                targetFormat = MimeType.MARKDOWN,
            )

        val encrypted = SensitiveEntityFieldEncryptors.encrypt(entity, healer)

        assertEncrypted(encrypted.json)
        assertEncrypted(encrypted.title)
        assertEncrypted(encrypted.author)
    }

    @Test
    fun encrypt_studioMediaAndCredentials_encryptsOnlyCatalogedSensitiveValues() {
        val media =
            StudioMediaEntity(
                id = "media-1",
                prompt = "draw family",
                mediaUri = "file://pic.png",
                mediaType = "IMAGE",
                remoteUrl = "https://storage/media.png",
                remoteThumbnailUrl = "https://storage/thumb.png",
                stagingRemoteUrl = "https://storage/staging.png",
            )
        val credentials =
            ApiCredentialsEntity(
                id = ApiCredentialsId("creds-1"),
                displayName = "OpenAI Main",
                provider = ApiProvider.OPENAI,
                baseUrl = "https://api.openai.com",
                modelId = "gpt",
                credentialAlias = "openai-main",
                encryptedApiKey = byteArrayOf(1, 2, 3),
            )

        val encryptedMedia = SensitiveEntityFieldEncryptors.encrypt(media, healer)
        val encryptedCredentials = SensitiveEntityFieldEncryptors.encrypt(credentials, healer)

        assertEncrypted(encryptedMedia.prompt)
        assertEncrypted(encryptedMedia.mediaUri)
        assertEncrypted(encryptedMedia.remoteUrl)
        assertEncrypted(encryptedMedia.remoteThumbnailUrl)
        assertEncrypted(encryptedMedia.stagingRemoteUrl)
        assertEquals("IMAGE", encryptedMedia.mediaType)
        assertEncrypted(encryptedCredentials.displayName)
        assertEncrypted(encryptedCredentials.baseUrl)
        assertEquals("openai-main", encryptedCredentials.credentialAlias)
    }

    // T4: entity-id AAD swap causes decrypt failure
    @Test
    fun decrypt_messageEntityWithMismatchedEntityId_aadFailsResult() {
        val cryptoManager = CryptoManagerImpl()
        val dek = cryptoManager.generateDek()
        cryptoManager.cacheDek(dek).getOrThrow()

        val aadA = AadContext("MessageEntity", "content", "entity-a").toBytes()
        val aadB = AadContext("MessageEntity", "content", "entity-b").toBytes()

        val ciphertext = cryptoManager.encrypt("secret content", aadA).getOrThrow()
        val result = cryptoManager.decrypt(ciphertext, aadB)

        assertTrue(result.isFailure)
    }

    private fun assertEncrypted(value: String?) {
        assertTrue(value?.startsWith("PCENC:v1:aes-gcm:") == true)
    }

    private class FakeLocalDataEncryptionService : LocalDataEncryptionService {
        override fun encryptString(plaintext: String): String = "PCENC:v1:aes-gcm:$plaintext"

        override fun encryptString(
            plaintext: String,
            aad: ByteArray,
        ): String = "PCENC:v1:aes-gcm:$plaintext"

        override fun decryptString(stored: String): String = stored.removePrefix("PCENC:v1:aes-gcm:")

        override fun decryptString(
            stored: String,
            aad: ByteArray,
        ): String = stored.removePrefix("PCENC:v1:aes-gcm:")

        override fun isEncrypted(stored: String): Boolean = stored.startsWith("PCENC:v1:aes-gcm:")
    }
}
