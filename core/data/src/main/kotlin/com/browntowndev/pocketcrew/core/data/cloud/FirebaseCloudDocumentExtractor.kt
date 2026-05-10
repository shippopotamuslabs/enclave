package com.browntowndev.pocketcrew.core.data.cloud

import com.browntowndev.pocketcrew.domain.port.cloud.CloudDocumentExtractorPort
import com.browntowndev.pocketcrew.domain.port.media.FileAttachmentMetadata
import com.browntowndev.pocketcrew.domain.port.media.FileAttachmentPort
import com.browntowndev.pocketcrew.domain.port.security.CryptoManagerPort
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseCloudDocumentExtractor @Inject constructor(
    private val functions: FirebaseFunctions,
    private val storage: FirebaseStorage,
    private val auth: FirebaseAuth,
    private val cryptoManager: CryptoManagerPort,
    private val fileAttachmentPort: FileAttachmentPort
) : CloudDocumentExtractorPort {

    override suspend fun extractText(uri: String, mimeType: String): FileAttachmentMetadata {
        val userId = auth.currentUser?.uid ?: throw IllegalStateException("User must be logged in for cloud extraction")
        val fileId = UUID.randomUUID().toString()
        val storagePath = "temp_extractions/$userId/$fileId.enc"

        // 1. Generate transient Key/IV
        val key = cryptoManager.generateRandomKey(32)
        val iv = cryptoManager.generateRandomIv(12)

        // 2. Read and Encrypt file
        val fileBytes = fileAttachmentPort.readBytes(uri)
        val encryptedBytes = cryptoManager.encryptWithKey(fileBytes, key, iv).getOrThrow()

        // 3. Upload to temp storage
        val storageRef = storage.getReference(storagePath)
        storageRef.putBytes(encryptedBytes).await()

        try {
            // 4. Call Firebase Function
            val result = functions.getHttpsCallable("extractDocumentText")
                .call(mapOf(
                    "path" to storagePath,
                    "key" to cryptoManager.encodeBase64(key),
                    "iv" to cryptoManager.encodeBase64(iv),
                    "mimeType" to mimeType
                ))
                .await()

            @Suppress("UNCHECKED_CAST")
            val data = result.data as Map<String, Any>
            val text = data["text"] as? String ?: ""
            val error = data["error"] as? String

            if (error != null) {
                throw Exception("Cloud extraction failed: $error")
            }

            // Get original filename
            val originalMetadata = fileAttachmentPort.readFileContent(uri)

            return FileAttachmentMetadata(
                name = originalMetadata.name,
                mimeType = mimeType,
                content = text
            )
        } finally {
            // Cleanup: The function should delete it, but we can try to delete it as well just in case
            // or rely on the scheduled cleanup. The design says function deletes it.
        }
    }
}
