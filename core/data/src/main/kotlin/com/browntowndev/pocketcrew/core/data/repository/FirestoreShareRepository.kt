package com.browntowndev.pocketcrew.core.data.repository

import android.util.Base64
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.share.SharedChat
import com.browntowndev.pocketcrew.domain.model.share.SharedMessage
import com.browntowndev.pocketcrew.domain.port.repository.ShareRepositoryPort
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
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
    private val loggingPort: LoggingPort
) : ShareRepositoryPort {

    companion object {
        private const val TAG = "FirestoreShareRepo"
    }

    override suspend fun shareChat(messages: List<Message>): SharedChat {
        val sharedMessages = messages.map { message ->
            SharedMessage(
                role = message.role.name.lowercase(),
                content = message.content.text,
                timestamp = message.createdAt ?: System.currentTimeMillis(),
                modelDisplayName = message.modelType?.name ?: ""
            )
        }

        val key = ByteArray(32)
        val iv = ByteArray(12)
        val salt = ByteArray(16)
        val secureRandom = SecureRandom()
        secureRandom.nextBytes(key)
        secureRandom.nextBytes(iv)
        secureRandom.nextBytes(salt)

        val json = Json.encodeToString(sharedMessages)
        val secretKey = SecretKeySpec(key, "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)
        val encryptedData = cipher.doFinal(json.toByteArray(StandardCharsets.UTF_8))

        val base64Payload = Base64.encodeToString(encryptedData, Base64.NO_WRAP)
        val base64Iv = Base64.encodeToString(iv, Base64.NO_WRAP)
        val base64Salt = Base64.encodeToString(salt, Base64.NO_WRAP)
        val base64Key = Base64.encodeToString(key, Base64.NO_WRAP)

        val result = functions.getHttpsCallable("uploadSharedChat")
            .call(
                mapOf(
                    "encryptedPayload" to base64Payload,
                    "iv" to base64Iv,
                    "salt" to base64Salt
                )
            )
            .await()
            
        @Suppress("UNCHECKED_CAST")
        val data = result.data as Map<String, Any>
        val shareId = data["shareId"] as String
        val originalShareUrl = data["shareUrl"] as String
        val shareUrl = "$originalShareUrl#key=$base64Key"
        
        return SharedChat(
            id = shareId,
            messages = sharedMessages,
            createdAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000L,
            shareUrl = shareUrl
        )
    }

    override suspend fun getSharedChat(shareId: String, decryptionKey: String?): SharedChat? {
        val doc = firestore.collection("shared_chats")
            .document(shareId)
            .get()
            .await()
            
        if (!doc.exists()) return null
        
        val encryptedPayload = doc.getString("encryptedPayload")
        val iv = doc.getString("iv")

        val messages = if (encryptedPayload != null && iv != null && decryptionKey != null) {
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
                Json.decodeFromString<List<SharedMessage>>(json)
            } catch (e: Exception) {
                loggingPort.error(TAG, "Decryption failed", e)
                emptyList()
            }
        } else {
            emptyList()
        }
        
        return SharedChat(
            id = doc.getString("id") ?: "",
            messages = messages,
            createdAt = doc.getLong("createdAt") ?: 0L,
            expiresAt = doc.getLong("expiresAt") ?: 0L,
            shareUrl = doc.getString("shareUrl") ?: ""
        )
    }
}
