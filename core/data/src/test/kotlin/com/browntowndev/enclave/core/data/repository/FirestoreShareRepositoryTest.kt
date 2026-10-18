@file:Suppress("ForbiddenMockKInTests")

// MockK retained only for the Firebase Firestore / Functions / Storage SDK surface, which has no JVM fake.
package com.browntowndev.enclave.core.data.repository

import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.core.testing.FakeStudioRepository
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.Content
import com.browntowndev.enclave.domain.model.chat.Message
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.Role
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.HttpsCallableReference
import com.google.firebase.functions.HttpsCallableResult
import com.google.firebase.storage.FirebaseStorage
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class FirestoreShareRepositoryTest {
    private val firestore = mockk<FirebaseFirestore>(relaxed = true)
    private val functions = mockk<FirebaseFunctions>(relaxed = true)
    private val storage = mockk<FirebaseStorage>(relaxed = true)
    private val studioRepository = FakeStudioRepository()
    private val loggingPort = FakeLoggingPort()
    private lateinit var repository: FirestoreShareRepository

    @BeforeEach
    fun setup() {
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
            studioRepository.mediaBytes["file://image.jpg"] = imageBytes

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

            assertTrue(sharedChat.shareUrl.startsWith("https://enclave-app.com/share/share123?key="))
            assertTrue(oneTimeKey.isNotBlank())
            assertEquals("aes-gcm-v1", uploadPayload.captured["encryptionVersion"])
            assertNotNull(uploadPayload.captured["encryptedPayload"])
            assertNotNull(uploadPayload.captured["iv"])

            val mockDoc = mockk<com.google.firebase.firestore.DocumentSnapshot>(relaxed = true)
            val docRef = mockk<com.google.firebase.firestore.DocumentReference>(relaxed = true)
            val collection = mockk<com.google.firebase.firestore.CollectionReference>(relaxed = true)
            every { firestore.collection("shared_chats") } returns collection
            every { collection.document("share123") } returns docRef
            every { docRef.get() } returns Tasks.forResult(mockDoc)
            every { mockDoc.exists() } returns true
            every { mockDoc.getString("encryptedPayload") } returns
                uploadPayload.captured.getValue("encryptedPayload") as String
            every { mockDoc.getString("iv") } returns uploadPayload.captured.getValue("iv") as String

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
            studioRepository.mediaBytes["file://gen.jpg"] = ByteArray(100)

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
    fun `shareChat with chatId includes sourceChatId in encrypted payload and getSharedChat recovers it`() =
        runTest {
            val sourceChatId = ChatId("original-chat-99")
            val messages =
                listOf(
                    Message(
                        id = MessageId("msg1"),
                        chatId = sourceChatId,
                        content = Content(text = "Hello"),
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

            val sharedChat = repository.shareChat(messages, sourceChatId)
            val oneTimeKey = sharedChat.shareUrl.substringAfter("key=")

            val mockDoc = mockk<com.google.firebase.firestore.DocumentSnapshot>(relaxed = true)
            val docRef = mockk<com.google.firebase.firestore.DocumentReference>(relaxed = true)
            val collection = mockk<com.google.firebase.firestore.CollectionReference>(relaxed = true)
            every { firestore.collection("shared_chats") } returns collection
            every { collection.document("share123") } returns docRef
            every { docRef.get() } returns Tasks.forResult(mockDoc)
            every { mockDoc.exists() } returns true
            every { mockDoc.getString("encryptedPayload") } returns
                uploadPayload.captured.getValue("encryptedPayload") as String
            every { mockDoc.getString("iv") } returns uploadPayload.captured.getValue("iv") as String

            val decrypted = repository.getSharedChat("share123", oneTimeKey)

            assertEquals(sourceChatId.value, decrypted?.sourceChatId)
        }

    @Test
    fun `shareChat without chatId has null sourceChatId`() =
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
            val uploadPayload = slot<Map<String, Any>>()
            every { functions.getHttpsCallable("uploadSharedChat") } returns callable
            every { callable.call(capture(uploadPayload)) } returns Tasks.forResult(result)
            every { result.getData() } returns serverResultData()

            val sharedChat = repository.shareChat(messages, null)
            val oneTimeKey = sharedChat.shareUrl.substringAfter("key=")

            val mockDoc = mockk<com.google.firebase.firestore.DocumentSnapshot>(relaxed = true)
            val docRef = mockk<com.google.firebase.firestore.DocumentReference>(relaxed = true)
            val collection = mockk<com.google.firebase.firestore.CollectionReference>(relaxed = true)
            every { firestore.collection("shared_chats") } returns collection
            every { collection.document("share123") } returns docRef
            every { docRef.get() } returns Tasks.forResult(mockDoc)
            every { mockDoc.exists() } returns true
            every { mockDoc.getString("encryptedPayload") } returns
                uploadPayload.captured.getValue("encryptedPayload") as String
            every { mockDoc.getString("iv") } returns uploadPayload.captured.getValue("iv") as String

            val decrypted = repository.getSharedChat("share123", oneTimeKey)

            assertEquals(null, decrypted?.sourceChatId)
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
                                        com.browntowndev.enclave.domain.model.chat.FileAttachment(
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
            "shareUrl" to "https://enclave-app.com/share/share123",
        )
}
