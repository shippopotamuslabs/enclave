package com.browntowndev.pocketcrew.core.data.cloud

import com.browntowndev.pocketcrew.domain.port.security.CryptoManagerPort
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
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
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class FirebaseCloudDocumentGeneratorTest {
    private lateinit var storage: FirebaseStorage
    private lateinit var functions: FirebaseFunctions
    private lateinit var cryptoManager: CryptoManagerPort
    private lateinit var generator: FirebaseCloudDocumentGenerator

    @BeforeEach
    fun setup() {
        storage = mockk(relaxed = true)
        functions = mockk(relaxed = true)
        cryptoManager = mockk(relaxed = true)
        generator = FirebaseCloudDocumentGenerator({ "test-user-id" }, storage, functions, cryptoManager)
        mockkStatic("kotlinx.coroutines.tasks.TasksKt")
    }

    @AfterEach
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `generateWordDocument should upload encrypted markdown and return decrypted docx`() {
        val markdown = "# Test"
        val key = byteArrayOf(1)
        val iv = byteArrayOf(2)
        val ciphertext = byteArrayOf(3)
        val encryptedDoc = byteArrayOf(5)
        val decryptedDoc = byteArrayOf(6)

        every { cryptoManager.generateRandomKey(any()) } returns key
        every { cryptoManager.generateRandomIv(any()) } returns iv
        every { cryptoManager.encryptWithKey(any(), any(), any()) } returns Result.success(ciphertext)
        every { cryptoManager.encodeBase64(any()) } returns "base64"
        every { cryptoManager.decodeBase64(any()) } returns iv
        every { cryptoManager.decryptWithKey(any(), any(), any()) } returns Result.success(decryptedDoc)

        val storageRef = mockk<StorageReference>(relaxed = true)
        val fileRef = mockk<StorageReference>(relaxed = true)
        every { storage.reference } returns storageRef
        every { storageRef.child(any()) } returns fileRef
        
        val uploadTask = mockk<UploadTask>(relaxed = true)
        val snapshot = mockk<UploadTask.TaskSnapshot>(relaxed = true)
        every { fileRef.putBytes(any<ByteArray>()) } returns uploadTask
        coEvery { uploadTask.await() } returns snapshot

        val callable = mockk<HttpsCallableReference>(relaxed = true)
        every { functions.getHttpsCallable(any()) } returns callable
        
        val functionResult = mockk<HttpsCallableResult>(relaxed = true)
        every { functionResult.getData() } returns mapOf("path" to "result-path", "iv" to "base64-iv")
        
        val functionTask = mockk<Task<HttpsCallableResult>>(relaxed = true)
        every { callable.call(any()) } returns functionTask
        coEvery { functionTask.await() } returns functionResult

        val getBytesTask = mockk<Task<ByteArray>>(relaxed = true)
        every { fileRef.getBytes(any()) } returns getBytesTask
        coEvery { getBytesTask.await() } returns encryptedDoc

        runTest {
            val result = generator.generateWordDocument(markdown)

            if (result.isFailure) {
                println("Test failed with exception: ${result.exceptionOrNull()}")
            }

            assertTrue(result.isSuccess)
            assertArrayEquals(decryptedDoc, result.getOrNull())
        }
    }
}
