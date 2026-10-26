package com.browntowndev.enclave.core.data.repository

import com.browntowndev.enclave.domain.exception.SubscriptionRequiredException
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.Message
import com.browntowndev.enclave.domain.model.entitlement.EntitlementState
import com.browntowndev.enclave.domain.model.share.SharedChat
import com.browntowndev.enclave.domain.model.share.SharedMessage
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.repository.EntitlementPort
import com.browntowndev.enclave.domain.port.repository.ShareRepositoryPort
import com.browntowndev.enclave.domain.port.repository.StudioRepositoryPort
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreShareRepository
    @Inject
    constructor(
        private val firestore: FirebaseFirestore,
        private val functions: FirebaseFunctions,
        private val storage: FirebaseStorage,
        private val studioRepository: StudioRepositoryPort,
        private val loggingPort: LoggingPort,
        private val entitlementPort: EntitlementPort,
    ) : ShareRepositoryPort {
        private companion object {
            private const val TAG = "FirestoreShareRepo"
            const val ENCRYPTION_VERSION = "aes-gcm-v1"
            const val KEY_LENGTH_BYTES = 32
            const val IV_LENGTH_BYTES = 12
        }

        override suspend fun shareChat(
            messages: List<Message>,
            chatId: ChatId?,
        ): SharedChat {
            if (entitlementPort.entitlementState.value is EntitlementState.NotEntitled) {
                throw SubscriptionRequiredException()
            }
            val shareId =
                java.util.UUID
                    .randomUUID()
                    .toString()
            val mediaList = mutableListOf<com.browntowndev.enclave.domain.model.share.SharedMedia>()
            val mediaPaths = mutableListOf<String>()

            val key = ByteArray(KEY_LENGTH_BYTES)
            val salt = ByteArray(16)
            val secureRandom = SecureRandom()
            secureRandom.nextBytes(key)
            secureRandom.nextBytes(salt)

            val secretKey = SecretKeySpec(key, "AES")

            val sharedMessages =
                messages.map { message ->
                    val attachedImageMediaIds =
                        message.content.imageUris
                            .map { uri ->
                                val mediaId =
                                    java.util.UUID
                                        .randomUUID()
                                        .toString()
                                val bytes = studioRepository.readMediaBytes(uri)
                                if (bytes != null) {
                                    val encryptedBytes = encryptBytes(bytes, secretKey)
                                    val storagePath = "shared_chat_media/$shareId/$mediaId"
                                    storage.getReference(storagePath).putBytes(encryptedBytes).await()
                                    mediaPaths.add(storagePath)
                                    mediaList.add(
                                        com.browntowndev.enclave.domain.model.share.SharedMedia(
                                            id = mediaId,
                                            mediaType = "IMAGE",
                                            storagePath = storagePath,
                                            originalMime = "image/jpeg",
                                        ),
                                    )
                                    mediaId
                                } else {
                                    ""
                                }
                            }.filter { it.isNotEmpty() }

                    val generatedMediaIds =
                        message.content.generatedMediaUris
                            .map { uri ->
                                val mediaId =
                                    java.util.UUID
                                        .randomUUID()
                                        .toString()
                                val bytes = studioRepository.readMediaBytes(uri)
                                if (bytes != null) {
                                    val encryptedBytes = encryptBytes(bytes, secretKey)
                                    val storagePath = "shared_chat_media/$shareId/$mediaId"
                                    storage.getReference(storagePath).putBytes(encryptedBytes).await()
                                    mediaPaths.add(storagePath)
                                    mediaList.add(
                                        com.browntowndev.enclave.domain.model.share.SharedMedia(
                                            id = mediaId,
                                            mediaType = "IMAGE",
                                            storagePath = storagePath,
                                            originalMime = "image/jpeg",
                                        ),
                                    )
                                    mediaId
                                } else {
                                    ""
                                }
                            }.filter { it.isNotEmpty() }

                    val fileAttachments =
                        message.content.fileAttachments.map { attachment ->
                            com.browntowndev.enclave.domain.model.share.SharedFileAttachment(
                                name = attachment.name,
                                content = attachment.content,
                            )
                        }

                    SharedMessage(
                        role = message.role.name.lowercase(),
                        content = message.content.text,
                        timestamp = message.createdAt ?: System.currentTimeMillis(),
                        modelDisplayName = message.modelType?.name ?: "",
                        attachedImageMediaIds = attachedImageMediaIds,
                        generatedMediaIds = generatedMediaIds,
                        fileAttachments = fileAttachments,
                    )
                }

            val payload =
                com.browntowndev.enclave.domain.model.share.SharedChat(
                    id = shareId,
                    messages = sharedMessages,
                    media = mediaList,
                    createdAt = System.currentTimeMillis(),
                    expiresAt = System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000L,
                    shareUrl = "", // Populated by server
                    sourceChatId = chatId?.value,
                )

            val json = Json.encodeToString(payload)
            val encryptedData = encryptBytes(json.toByteArray(StandardCharsets.UTF_8), secretKey)
            val ivBytes = encryptedData.copyOfRange(0, IV_LENGTH_BYTES)
            val ciphertextBytes = encryptedData.copyOfRange(IV_LENGTH_BYTES, encryptedData.size)

            val base64Payload = Base64.getEncoder().encodeToString(ciphertextBytes)
            val base64Iv = Base64.getEncoder().encodeToString(ivBytes)
            val base64Salt = Base64.getEncoder().encodeToString(salt)
            val base64Key = Base64.getUrlEncoder().withoutPadding().encodeToString(key)

            val result =
                functions
                    .getHttpsCallable("uploadSharedChat")
                    .call(
                        mapOf(
                            "shareId" to shareId,
                            "encryptedPayload" to base64Payload,
                            "iv" to base64Iv,
                            "encryptionVersion" to ENCRYPTION_VERSION,
                            "salt" to base64Salt,
                            "mediaPaths" to mediaPaths,
                        ),
                    ).await()

            @Suppress("UNCHECKED_CAST")
            val data = result.data as Map<String, Any>
            val finalShareId = data["shareId"] as String
            val originalShareUrl = data["shareUrl"] as String
            val shareUrl = originalShareUrl.withOneTimeKey(base64Key)

            return payload.copy(id = finalShareId, shareUrl = shareUrl)
        }

        private fun encryptBytes(
            data: ByteArray,
            secretKey: SecretKeySpec,
        ): ByteArray {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val iv = ByteArray(IV_LENGTH_BYTES)
            SecureRandom().nextBytes(iv)
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)
            val ciphertext = cipher.doFinal(data)
            return iv + ciphertext
        }

        private fun decryptBytesWithIv(
            ciphertext: ByteArray,
            secretKey: SecretKeySpec,
            iv: ByteArray,
        ): ByteArray {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            return cipher.doFinal(ciphertext)
        }

        private fun decryptBytes(
            encryptedBytes: ByteArray,
            secretKey: SecretKeySpec,
        ): ByteArray {
            require(encryptedBytes.size > IV_LENGTH_BYTES) { "Encrypted payload too short" }
            val iv = encryptedBytes.copyOfRange(0, IV_LENGTH_BYTES)
            val ciphertext = encryptedBytes.copyOfRange(IV_LENGTH_BYTES, encryptedBytes.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            return cipher.doFinal(ciphertext)
        }

        private fun String.withOneTimeKey(base64Key: String): String {
            val encodedKey = URLEncoder.encode(base64Key, StandardCharsets.UTF_8.name())
            val fragmentIndex = indexOf('#')
            val baseUrl = if (fragmentIndex >= 0) substring(0, fragmentIndex) else this
            val fragment = if (fragmentIndex >= 0) substring(fragmentIndex) else ""
            val separator = if (baseUrl.contains("?")) "&" else "?"
            return "$baseUrl${separator}key=$encodedKey$fragment"
        }

        override suspend fun getSharedChat(
            shareId: String,
            decryptionKey: String?,
        ): SharedChat? {
            val doc =
                firestore
                    .collection("shared_chats")
                    .document(shareId)
                    .get()
                    .await()

            if (!doc.exists()) return null

            val encryptedPayload = doc.getString("encryptedPayload")
            val ivString = doc.getString("iv")

            val payload =
                if (encryptedPayload != null && decryptionKey != null) {
                    try {
                        val keyBytes = Base64.getUrlDecoder().decode(decryptionKey)
                        val secretKey = SecretKeySpec(keyBytes, "AES")
                        val decryptedBytes =
                            if (!ivString.isNullOrEmpty()) {
                                val iv = Base64.getDecoder().decode(ivString)
                                val ciphertext = Base64.getDecoder().decode(encryptedPayload)
                                decryptBytesWithIv(ciphertext, secretKey, iv)
                            } else {
                                val encryptedBytes = Base64.getDecoder().decode(encryptedPayload)
                                decryptBytes(encryptedBytes, secretKey)
                            }
                        val json = String(decryptedBytes, StandardCharsets.UTF_8)
                        Json.decodeFromString<com.browntowndev.enclave.domain.model.share.SharedChat>(json)
                    } catch (e: Exception) {
                        loggingPort.error(TAG, "Decryption failed", e)
                        null
                    }
                } else {
                    null
                }

            return payload ?: com.browntowndev.enclave.domain.model.share.SharedChat(
                id = doc.getString("id") ?: "",
                messages = emptyList(),
                media = emptyList(),
                createdAt = doc.getLong("createdAt") ?: 0L,
                expiresAt = doc.getLong("expiresAt") ?: 0L,
                shareUrl = doc.getString("shareUrl") ?: "",
            )
        }
    }
