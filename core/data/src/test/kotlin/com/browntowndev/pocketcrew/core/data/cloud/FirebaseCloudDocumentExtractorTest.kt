package com.browntowndev.pocketcrew.core.data.cloud

import com.browntowndev.pocketcrew.domain.port.media.FileAttachmentMetadata
import com.browntowndev.pocketcrew.domain.port.media.FileAttachmentPort
import com.browntowndev.pocketcrew.domain.port.security.CryptoManagerPort
import com.google.android.gms.tasks.Task
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.HttpsCallableReference
import com.google.firebase.functions.HttpsCallableResult
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.UploadTask
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class FirebaseCloudDocumentExtractorTest {
    private lateinit var storage: FirebaseStorage
    private lateinit var functions: FirebaseFunctions
    private lateinit var cryptoManager: CryptoManagerPort
    private lateinit var fileAttachmentPort: FileAttachmentPort
    private lateinit var extractor: FirebaseCloudDocumentExtractor

    @BeforeEach
    fun setup() {
        storage = mockk(relaxed = true)
        functions = mockk(relaxed = true)
        cryptoManager = mockk(relaxed = true)
        fileAttachmentPort = mockk(relaxed = true)
        extractor = FirebaseCloudDocumentExtractor(
            functions,
            storage,
            { "test-user-id" },
            cryptoManager,
            fileAttachmentPort
        )
        mockkStatic("kotlinx.coroutines.tasks.TasksKt")
    }

    @AfterEach
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `extractText should upload encrypted file and return extracted text`() = runTest {
        val uri = "file://test.docx"
        val mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        val fileBytes = byteArrayOf(1)
        val key = byteArrayOf(2)
        val iv = byteArrayOf(3)
        val encryptedBytes = byteArrayOf(4)
        val extractedText = "Extracted text content"

        coEvery { fileAttachmentPort.readBytes(uri) } returns fileBytes
        every { cryptoManager.generateRandomKey(any()) } returns key
        every { cryptoManager.generateRandomIv(any()) } returns iv
        every { cryptoManager.encryptWithKey(fileBytes, key, iv) } returns Result.success(encryptedBytes)
        every { cryptoManager.encodeBase64(any()) } returns "base64"

        val storageRef = mockk<StorageReference>(relaxed = true)
        every { storage.getReference(any()) } returns storageRef
        
        val uploadTask = mockk<UploadTask>(relaxed = true)
        val snapshot = mockk<UploadTask.TaskSnapshot>(relaxed = true)
        every { storageRef.putBytes(any<ByteArray>()) } returns uploadTask
        coEvery { uploadTask.await() } returns snapshot

        val callable = mockk<HttpsCallableReference>(relaxed = true)
        every { functions.getHttpsCallable(any()) } returns callable
        
        val functionResult = mockk<HttpsCallableResult>(relaxed = true)
        every { functionResult.getData() } returns mapOf("text" to extractedText)
        
        val functionTask = mockk<Task<HttpsCallableResult>>(relaxed = true)
        every { callable.call(any()) } returns functionTask
        coEvery { functionTask.await() } returns functionResult

        coEvery { fileAttachmentPort.readFileContent(uri) } returns FileAttachmentMetadata(name = "test.docx", mimeType = mimeType, content = "")

        val result = extractor.extractText(uri, mimeType)

        assertEquals("test.docx", result.name)
        assertEquals(mimeType, result.mimeType)
        assertEquals(extractedText, result.content)
    }
}
