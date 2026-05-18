package com.browntowndev.pocketcrew.core.data.repository

import android.util.Log
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.Content
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.StudioRepositoryPort
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.HttpsCallableReference
import com.google.firebase.functions.HttpsCallableResult
import com.google.firebase.storage.FirebaseStorage
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class FirestoreShareRepositoryTest {
    private val firestore = mockk<FirebaseFirestore>(relaxed = true)
    private val functions = mockk<FirebaseFunctions>(relaxed = true)
    private val storage = mockk<FirebaseStorage>(relaxed = true)
    private val studioRepository = mockk<StudioRepositoryPort>(relaxed = true)
    private val loggingPort = mockk<LoggingPort>(relaxed = true)
    private lateinit var repository: FirestoreShareRepository

    @BeforeEach
    fun setup() {
        mockkStatic(Log::class)
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

        mockkStatic(UUID::class)
        every { UUID.randomUUID().toString() } returns "mock-uuid"

        val storageRef = mockk<com.google.firebase.storage.StorageReference>(relaxed = true)
        val uploadTask = mockk<com.google.firebase.storage.UploadTask>(relaxed = true)
        every { storage.getReference(any<String>()) } returns storageRef
        every { storageRef.putBytes(any<ByteArray>()) } returns uploadTask
        every { uploadTask.isComplete } returns true
        every { uploadTask.isSuccessful } returns true
        every { uploadTask.exception } returns null
        every { uploadTask.result } returns mockk(relaxed = true)

        repository = FirestoreShareRepository(firestore, functions, storage, studioRepository, loggingPort)
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Log::class)
        unmockkStatic(UUID::class)
    }

    @Test
    fun `shareChat with attached image uploads to storage and includes mediaId`() =
        runTest {
            val messages =
                listOf(
                    Message(
                        id = MessageId("msg1"),
                        chatId = ChatId("chat1"),
                        content = Content(text = "Hello", imageUris = listOf("file://image.jpg")),
                        role = Role.USER,
                        createdAt = 1000L,
                    ),
                )
            val imageBytes = ByteArray(100)
            coEvery { studioRepository.readMediaBytes("file://image.jpg") } returns imageBytes

            val callable = mockk<HttpsCallableReference>(relaxed = true)
            val result = mockk<HttpsCallableResult>(relaxed = true)
            every { functions.getHttpsCallable("uploadSharedChat") } returns callable
            every { callable.call(any()) } returns Tasks.forResult(result)
            every { result.getData() } returns serverResultData()

            val sharedChat = repository.shareChat(messages)

            assertEquals("share123", sharedChat.id)
            assertTrue(
                sharedChat.messages
                    .first()
                    .attachedImageMediaIds
                    .isNotEmpty(),
            )
        }

    @Test
    fun `shareChat with empty media includes no mediaIds`() =
        runTest {
            val messages =
                listOf(
                    Message(
                        id = MessageId("msg1"),
                        chatId = ChatId("chat1"),
                        content = Content(text = "Hello"),
                        role = Role.USER,
                        createdAt = 1000L,
                    ),
                )

            val callable = mockk<HttpsCallableReference>(relaxed = true)
            val result = mockk<HttpsCallableResult>(relaxed = true)
            every { functions.getHttpsCallable("uploadSharedChat") } returns callable
            every { callable.call(any()) } returns Tasks.forResult(result)
            every { result.getData() } returns serverResultData()

            val sharedChat = repository.shareChat(messages)

            assertTrue(
                sharedChat.messages
                    .first()
                    .attachedImageMediaIds
                    .isEmpty(),
            )
            assertTrue(
                sharedChat.messages
                    .first()
                    .generatedMediaIds
                    .isEmpty(),
            )
        }

    @Test
    fun `getSharedChat works`() =
        runTest {
            val mockDoc = mockk<com.google.firebase.firestore.DocumentSnapshot>(relaxed = true)
            val docRef = mockk<com.google.firebase.firestore.DocumentReference>(relaxed = true)
            val collection = mockk<com.google.firebase.firestore.CollectionReference>(relaxed = true)

            every { firestore.collection("shared_chats") } returns collection
            every { collection.document(any()) } returns docRef
            every { docRef.get() } returns Tasks.forResult(mockDoc)
            every { mockDoc.exists() } returns true

            val result = repository.getSharedChat("share123", "key")
            assertNotNull(result)
        }

    @Test
    fun `shareChat encrypts payload with url safe one time key and getSharedChat decrypts it`() =
        runTest {
            val messages =
                listOf(
                    Message(
                        id = MessageId("msg1"),
                        chatId = ChatId("chat1"),
                        content = Content(text = "Encrypted hello"),
                        role = Role.USER,
                        createdAt = 1000L,
                    ),
                )

            val callable = mockk<HttpsCallableReference>(relaxed = true)
            val result = mockk<HttpsCallableResult>(relaxed = true)
            val uploadPayload = slot<Map<String, Any>>()
            every { functions.getHttpsCallable("uploadSharedChat") } returns callable
            every { callable.call(capture(uploadPayload)) } returns Tasks.forResult(result)
            every { result.getData() } returns serverResultData()

            val sharedChat = repository.shareChat(messages)
            val oneTimeKey = sharedChat.shareUrl.substringAfter("key=")

            assertTrue(sharedChat.shareUrl.startsWith("https://pocketcrew.app/share/share123?key="))
            assertTrue(oneTimeKey.isNotBlank())
            assertEquals("aes-gcm-iv-prefixed-v1", uploadPayload.captured["encryptionVersion"])
            assertNotNull(uploadPayload.captured["encryptedPayload"])

            val mockDoc = mockk<com.google.firebase.firestore.DocumentSnapshot>(relaxed = true)
            val docRef = mockk<com.google.firebase.firestore.DocumentReference>(relaxed = true)
            val collection = mockk<com.google.firebase.firestore.CollectionReference>(relaxed = true)
            every { firestore.collection("shared_chats") } returns collection
            every { collection.document("share123") } returns docRef
            every { docRef.get() } returns Tasks.forResult(mockDoc)
            every { mockDoc.exists() } returns true
            every { mockDoc.getString("encryptedPayload") } returns
                uploadPayload.captured.getValue("encryptedPayload") as String

            val decrypted = repository.getSharedChat("share123", oneTimeKey)

            assertNotNull(decrypted)
            assertEquals("Encrypted hello", decrypted?.messages?.single()?.content)
        }

    @Test
    fun `shareChat with generated media uploads to storage`() =
        runTest {
            val messages =
                listOf(
                    Message(
                        id = MessageId("msg1"),
                        chatId = ChatId("chat1"),
                        content = Content(text = "Generated", generatedMediaUris = listOf("file://gen.jpg")),
                        role = Role.ASSISTANT,
                        createdAt = 1000L,
                    ),
                )
            coEvery { studioRepository.readMediaBytes("file://gen.jpg") } returns ByteArray(100)

            val callable = mockk<HttpsCallableReference>(relaxed = true)
            val result = mockk<HttpsCallableResult>(relaxed = true)
            every { functions.getHttpsCallable("uploadSharedChat") } returns callable
            every { callable.call(any()) } returns Tasks.forResult(result)
            every { result.getData() } returns serverResultData()

            val sharedChat = repository.shareChat(messages)

            assertTrue(
                sharedChat.messages
                    .first()
                    .generatedMediaIds
                    .isNotEmpty(),
            )
        }

    @Test
    fun `shareChat with file attachment inlines content`() =
        runTest {
            val messages =
                listOf(
                    Message(
                        id = MessageId("msg1"),
                        chatId = ChatId("chat1"),
                        content =
                            Content(
                                text = "Attached",
                                fileAttachments =
                                    listOf(
                                        com.browntowndev.pocketcrew.domain.model.chat.FileAttachment(
                                            name = "test.txt",
                                            content = "file content",
                                        ),
                                    ),
                            ),
                        role = Role.USER,
                        createdAt = 1000L,
                    ),
                )

            val callable = mockk<HttpsCallableReference>(relaxed = true)
            val result = mockk<HttpsCallableResult>(relaxed = true)
            every { functions.getHttpsCallable("uploadSharedChat") } returns callable
            every { callable.call(any()) } returns Tasks.forResult(result)
            every { result.getData() } returns serverResultData()

            val sharedChat = repository.shareChat(messages)

            assertTrue(
                sharedChat.messages
                    .first()
                    .fileAttachments
                    .isNotEmpty(),
            )
            assertEquals(
                "test.txt",
                sharedChat.messages
                    .first()
                    .fileAttachments
                    .first()
                .name,
            )
        }

    private fun serverResultData(): Map<String, String> =
        mapOf(
            "shareId" to "share123",
            "shareUrl" to "https://pocketcrew.app/share/share123",
        )
}
