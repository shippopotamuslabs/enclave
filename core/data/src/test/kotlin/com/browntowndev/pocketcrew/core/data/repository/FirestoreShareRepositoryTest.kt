package com.browntowndev.pocketcrew.core.data.repository

import android.util.Base64
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.chat.Content
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.HttpsCallableReference
import com.google.firebase.functions.HttpsCallableResult
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.Date
import android.util.Log

class FirestoreShareRepositoryTest {

    private val firestore = mockk<FirebaseFirestore>(relaxed = true)
    private val functions = mockk<FirebaseFunctions>(relaxed = true)
    private val loggingPort = mockk<LoggingPort>(relaxed = true)
    private lateinit var repository: FirestoreShareRepository

    @BeforeEach
    fun setup() {
        mockkStatic(Log::class)
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

        mockkStatic(Base64::class)
        every { Base64.encodeToString(any<ByteArray>(), any<Int>()) } returns "mock-base64"
        every { Base64.decode(any<String>(), any<Int>()) } returns ByteArray(32)

        repository = FirestoreShareRepository(firestore, functions, loggingPort)
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Base64::class)
        unmockkStatic(Log::class)
    }

    @Test
    fun `getSharedChat works`() = runTest {
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
}
