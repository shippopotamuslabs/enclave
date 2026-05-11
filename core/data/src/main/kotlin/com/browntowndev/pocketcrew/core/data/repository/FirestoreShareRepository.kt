package com.browntowndev.pocketcrew.core.data.repository

import android.util.Base64
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.share.SharedChat
import com.browntowndev.pocketcrew.domain.model.share.SharedMessage
import com.browntowndev.pocketcrew.domain.port.repository.ShareRepositoryPort
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage
import com.browntowndev.pocketcrew.domain.port.repository.StudioRepositoryPort
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import java.nio.charset.StandardCharsets

import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort

@Singleton
class FirestoreShareRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val functions: FirebaseFunctions,
    private val storage: FirebaseStorage,
    private val studioRepository: StudioRepositoryPort,
    private val loggingPort: LoggingPort
) : ShareRepositoryPort {

    companion object {
        private const val TAG = "FirestoreShareRepo"
    }

    override suspend fun shareChat(messages: List<Message>): SharedChat {
        val shareId = java.util.UUID.randomUUID().toString()
        val mediaList = mutableListOf<com.browntowndev.pocketcrew.domain.model.share.SharedMedia>()
        val mediaPaths = mutableListOf<String>()

        val key = ByteArray(32)
        val iv = ByteArray(12)
        val salt = ByteArray(16)
        val secureRandom = SecureRandom()
        secureRandom.nextBytes(key)
        secureRandom.nextBytes(iv)
        secureRandom.nextBytes(salt)
        
        val secretKey = SecretKeySpec(key, "AES")

        val sharedMessages = messages.map { message ->
            val attachedImageMediaIds = message.content.imageUris.map { uri ->
                val mediaId = java.util.UUID.randomUUID().toString()
                val bytes = studioRepository.readMediaBytes(uri)
                if (bytes != null) {
                    val encryptedBytes = encryptBytes(bytes, secretKey, iv)
                    val storagePath = "shared_chat_media/$shareId/$mediaId"
                    storage.getReference(storagePath).putBytes(encryptedBytes).await()
                    mediaPaths.add(storagePath)
                    mediaList.add(
                        com.browntowndev.pocketcrew.domain.model.share.SharedMedia(
                            id = mediaId,
                            mediaType = "IMAGE",
                            storagePath = storagePath,
                            originalMime = "image/jpeg"
                        )
                    )
                    mediaId
                } else ""
            }.filter { it.isNotEmpty() }

            val generatedMediaIds = message.content.generatedMediaUris.map { uri ->
                val mediaId = java.util.UUID.randomUUID().toString()
                val bytes = studioRepository.readMediaBytes(uri)
                if (bytes != null) {
                    val encryptedBytes = encryptBytes(bytes, secretKey, iv)
                    val storagePath = "shared_chat_media/$shareId/$mediaId"
                    storage.getReference(storagePath).putBytes(encryptedBytes).await()
                    mediaPaths.add(storagePath)
                    mediaList.add(
                        com.browntowndev.pocketcrew.domain.model.share.SharedMedia(
                            id = mediaId,
                            mediaType = "IMAGE",
                            storagePath = storagePath,
                            originalMime = "image/jpeg"
                        )
                    )
                    mediaId
                } else ""
            }.filter { it.isNotEmpty() }

            val fileAttachments = message.content.fileAttachments.map { attachment ->
                com.browntowndev.pocketcrew.domain.model.share.SharedFileAttachment(
                    name = attachment.name,
                    content = attachment.content
                )
            }

            SharedMessage(
                role = message.role.name.lowercase(),
                content = message.content.text,
                timestamp = message.createdAt ?: System.currentTimeMillis(),
                modelDisplayName = message.modelType?.name ?: "",
                attachedImageMediaIds = attachedImageMediaIds,
                generatedMediaIds = generatedMediaIds,
                fileAttachments = fileAttachments
            )
        }

        val payload = com.browntowndev.pocketcrew.domain.model.share.SharedChat(
            id = shareId,
            messages = sharedMessages,
            media = mediaList,
            createdAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000L,
            shareUrl = "" // Populated by server
        )

        val json = Json.encodeToString(payload)
        val encryptedData = encryptBytes(json.toByteArray(StandardCharsets.UTF_8), secretKey, iv)

        val base64Payload = Base64.encodeToString(encryptedData, Base64.NO_WRAP)
        val base64Iv = Base64.encodeToString(iv, Base64.NO_WRAP)
        val base64Salt = Base64.encodeToString(salt, Base64.NO_WRAP)
        val base64Key = Base64.encodeToString(key, Base64.NO_WRAP)

        val result = functions.getHttpsCallable("uploadSharedChat")
            .call(
                mapOf(
                    "shareId" to shareId,
                    "encryptedPayload" to base64Payload,
                    "iv" to base64Iv,
                    "salt" to base64Salt,
                    "mediaPaths" to mediaPaths
                )
            )
            .await()
            
        @Suppress("UNCHECKED_CAST")
        val data = result.data as Map<String, Any>
        val finalShareId = data["shareId"] as String
        val originalShareUrl = data["shareUrl"] as String
        val shareUrl = "$originalShareUrl#key=$base64Key"
        
        return payload.copy(id = finalShareId, shareUrl = shareUrl)
    }

    private fun encryptBytes(data: ByteArray, secretKey: SecretKeySpec, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)
        return cipher.doFinal(data)
    }

    override suspend fun getSharedChat(shareId: String, decryptionKey: String?): SharedChat? {
        val doc = firestore.collection("shared_chats")
            .document(shareId)
            .get()
            .await()
            
        if (!doc.exists()) return null
        
        val encryptedPayload = doc.getString("encryptedPayload")
        val iv = doc.getString("iv")

        val payload = if (encryptedPayload != null && iv != null && decryptionKey != null) {
            try {
                val keyBytes = Base64.decode(decryptionKey, Base64.NO_WRAP)
                val ivBytes = Base64.decode(iv, Base64.NO_WRAP)
                val encryptedBytes = Base64.decode(encryptedPayload, Base64.DEFAULT)

                val secretKey = SecretKeySpec(keyBytes, "AES")
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                val spec = GCMParameterSpec(128, ivBytes)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
                
                val decryptedBytes = cipher.doFinal(encryptedBytes)
                val json = String(decryptedBytes, StandardCharsets.UTF_8)
                Json.decodeFromString<com.browntowndev.pocketcrew.domain.model.share.SharedChat>(json)
            } catch (e: Exception) {
                loggingPort.error(TAG, "Decryption failed", e)
                null
            }
        } else {
            null
        }
        
        return payload ?: com.browntowndev.pocketcrew.domain.model.share.SharedChat(
            id = doc.getString("id") ?: "",
            messages = emptyList(),
            media = emptyList(),
            createdAt = doc.getLong("createdAt") ?: 0L,
            expiresAt = doc.getLong("expiresAt") ?: 0L,
            shareUrl = doc.getString("shareUrl") ?: ""
        )
    }
}
