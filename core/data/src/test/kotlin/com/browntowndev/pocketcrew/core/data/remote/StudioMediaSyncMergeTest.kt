package com.browntowndev.pocketcrew.core.data.remote

import com.browntowndev.pocketcrew.core.data.local.ChatSummaryDao
import com.browntowndev.pocketcrew.core.data.local.StudioMediaDao
import com.browntowndev.pocketcrew.core.data.local.StudioMediaEntity
import com.browntowndev.pocketcrew.core.data.repository.FakeLocalDataEncryptionService
import com.browntowndev.pocketcrew.core.data.sync.EmbeddingRegenerationCoordinator
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.security.CryptoManagerPort
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QueryDocumentSnapshot
import com.google.firebase.firestore.QuerySnapshot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StudioMediaSyncMergeTest {
    private val firestore = mockk<FirebaseFirestore>(relaxed = true)
    private val studioMediaDao = mockk<StudioMediaDao>(relaxed = true)
    private val cryptoManager = mockk<CryptoManagerPort>(relaxed = true)
    private val loggingPort = mockk<LoggingPort>(relaxed = true)
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val embeddingCoordinator = mockk<EmbeddingRegenerationCoordinator>(relaxed = true)
    private val embeddingDao = mockk<com.browntowndev.pocketcrew.core.data.local.EmbeddingDao>(relaxed = true)
    private val chatSummaryDao = mockk<ChatSummaryDao>(relaxed = true)

    private lateinit var dataSource: FirestoreDataSource
    private val listenerSlot = slot<EventListener<QuerySnapshot>>()

    @BeforeEach
    fun setup() {
        val studioCollection = mockk<CollectionReference>(relaxed = true)
        every { firestore.collection("users/test-user/studio_gallery") } returns studioCollection
        every { studioCollection.addSnapshotListener(capture(listenerSlot)) } returns mockk(relaxed = true)
        every { cryptoManager.hasCachedKey() } returns true
        every { cryptoManager.decrypt(any()) } answers { Result.success(String(it.invocation.args[0] as ByteArray)) }

        dataSource =
            createTestFirestoreDataSource(
                documentDao = mockk(relaxed = true),
                firestore = firestore,
                chatDao = mockk(relaxed = true),
                messageDao = mockk(relaxed = true),
                messageTranscriptSegmentDao = mockk(relaxed = true),
                memoriesDao = mockk(relaxed = true),
                webSearchSourceDao = mockk(relaxed = true),
                ttsProviderDao = mockk(relaxed = true),
                mediaProviderDao = mockk(relaxed = true),
                apiModelConfigurationsDao = mockk(relaxed = true),
                localModelsDao = mockk(relaxed = true),
                apiCredentialsDao = mockk(relaxed = true),
                localModelConfigurationsDao = mockk(relaxed = true),
                defaultModelsDao = mockk(relaxed = true),
                studioMediaDao = studioMediaDao,
                studioAlbumDao = mockk(relaxed = true),
                messageFileDao = mockk(relaxed = true),
                messageVisionAnalysisDao = mockk(relaxed = true),
                chatSummaryDao = chatSummaryDao,
                embeddingDao = embeddingDao,
                apiKeyManager = mockk(relaxed = true),
                cryptoManager = cryptoManager,
                loggingPort = loggingPort,
                externalScope = testScope,
                userIdProvider = { "test-user" },
                embeddingCoordinator = embeddingCoordinator,
                localEncryption = FakeLocalDataEncryptionService(),
            )

        dataSource.startListening()
    }

    @Test
    fun `MODIFIED event preserves local mediaUri when remoteUrl is null`() =
        runTest {
            val mediaId = "media-1"
            val localPath = "file:///data/user/0/com.pocketcrew/files/studio_123.jpg"

            val existingLocal =
                StudioMediaEntity(
                    id = mediaId,
                    prompt = "A sunset",
                    mediaUri = localPath,
                    mediaType = "IMAGE",
                    remoteUrl = null,
                    pendingSync = 1,
                    updatedAt = 1000L,
                )

            coEvery { studioMediaDao.getMediaById(mediaId) } returns existingLocal

            // Simulate MODIFIED event from Firestore
            val mockDoc = mockk<QueryDocumentSnapshot>(relaxed = true)
            every { mockDoc.id } returns mediaId
            every { mockDoc.getBlob("prompt") } returns Blob.fromBytes("A sunset".toByteArray())
            every { mockDoc.getString("media_type") } returns "IMAGE"
            every { mockDoc.getLong("created_at") } returns 1000L
            every { mockDoc.getLong("updated_at") } returns 2000L
            every { mockDoc.getBoolean("deleted") } returns false
            every { mockDoc.getString("album_id") } returns null
            every { mockDoc.getBlob("remote_url") } returns null
            every { mockDoc.getBlob("remote_thumbnail_url") } returns null

            val change = mockk<DocumentChange>()
            every { change.type } returns DocumentChange.Type.MODIFIED
            every { change.document } returns mockDoc

            val snapshot = mockk<QuerySnapshot>()
            every { snapshot.documentChanges } returns listOf(change)

            listenerSlot.captured.onEvent(snapshot, null)

            coVerify {
                studioMediaDao.insertMedia(
                    match {
                        it.id == mediaId && it.mediaUri == localPath && it.remoteUrl == null
                    },
                )
            }
        }

    @Test
    fun `MODIFIED event updates mediaUri when remoteUrl is available`() =
        runTest {
            val mediaId = "media-1"
            val localPath = "file:///data/user/0/com.pocketcrew/files/studio_123.jpg"
            val remoteUrl = "https://firebasestorage.googleapis.com/.../media-1"

            val existingLocal =
                StudioMediaEntity(
                    id = mediaId,
                    prompt = "A sunset",
                    mediaUri = localPath,
                    mediaType = "IMAGE",
                    remoteUrl = null,
                    pendingSync = 1,
                    updatedAt = 1000L,
                )

            coEvery { studioMediaDao.getMediaById(mediaId) } returns existingLocal

            // Simulate MODIFIED event from Firestore with a remote_url
            val mockDoc = mockk<QueryDocumentSnapshot>(relaxed = true)
            every { mockDoc.id } returns mediaId
            every { mockDoc.getBlob("prompt") } returns Blob.fromBytes("A sunset".toByteArray())
            every { mockDoc.getString("media_type") } returns "IMAGE"
            every { mockDoc.getLong("created_at") } returns 1000L
            every { mockDoc.getLong("updated_at") } returns 2000L
            every { mockDoc.getBoolean("deleted") } returns false
            every { mockDoc.getString("album_id") } returns null
            every { mockDoc.getBlob("remote_url") } returns Blob.fromBytes(remoteUrl.toByteArray())
            every { mockDoc.getBlob("remote_thumbnail_url") } returns null

            val change = mockk<DocumentChange>()
            every { change.type } returns DocumentChange.Type.MODIFIED
            every { change.document } returns mockDoc

            val snapshot = mockk<QuerySnapshot>()
            every { snapshot.documentChanges } returns listOf(change)

            listenerSlot.captured.onEvent(snapshot, null)

            coVerify {
                studioMediaDao.insertMedia(
                    match {
                        it.id == mediaId && it.mediaUri == remoteUrl && it.remoteUrl == remoteUrl
                    },
                )
            }
        }
}
