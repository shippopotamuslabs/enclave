package com.browntowndev.enclave.core.data.remote.firestore

import com.browntowndev.enclave.core.data.local.StudioMediaEntity
import com.browntowndev.enclave.core.data.repository.FakeLocalDataEncryptionService
import com.browntowndev.enclave.domain.port.security.CryptoManagerPort
import com.google.firebase.firestore.Blob
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Replaces legacy [com.browntowndev.enclave.core.data.remote.StudioMediaSyncMergeTest]:
 * studio merge behavior is exercised through the event-feed applier path (handler upsertLocal).
 */
class StudioMediaSyncEventFeedTest {
    @Test
    fun `event feed studio media upsert preserves local mediaUri when remote url is null`() =
        runTest {
            val mediaId = "media-1"
            val localPath = "file:///data/user/0/com.browntowndev.enclave/files/studio_123.jpg"
            val studioMediaDao = mockk<com.browntowndev.enclave.core.data.local.StudioMediaDao>(relaxed = true)
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
            val captured = captureStudioMediaUpserts(studioMediaDao)
            val cryptoManager = mockk<CryptoManagerPort>(relaxed = true)
            every { cryptoManager.hasCachedKey() } returns true
            val (applier, sourceDocuments) =
                buildStudioMediaHydrationApplier(
                    studioMediaDao = studioMediaDao,
                    cryptoManager = cryptoManager,
                    localEncryption = FakeLocalDataEncryptionService(),
                )
            sourceDocuments.put(
                "users/alice/studio_gallery/$mediaId",
                mapOf(
                    "prompt" to Blob.fromBytes("A sunset".toByteArray()),
                    "media_type" to "IMAGE",
                    "created_at" to 1000L,
                    "updated_at" to 2000L,
                    "deleted" to false,
                    "album_id" to null,
                    "remote_url" to null,
                    "remote_thumbnail_url" to null,
                ),
            )
            applier.apply(
                userId = "alice",
                event =
                    FirestoreSyncEvent(
                        sequence = 1,
                        schemaVersion = 1,
                        operation = "upsert",
                        collectionPath = "studio_gallery",
                        entityType = "studio_gallery",
                        entityId = mediaId,
                        entityPath = "users/alice/studio_gallery/$mediaId",
                        sourceExists = true,
                        sourceUpdatedAt = 2000L,
                        sourceDeleted = false,
                        deviceId = "device-remote",
                        clientMutationId = null,
                        triggerEventId = "event-1",
                        emittedAtMillis = 1700000001001,
                    ),
            )

            assertEquals(1, captured.size)
            assertEquals(localPath, captured.first().mediaUri)
            assertEquals(null, captured.first().remoteUrl)
            coVerify(exactly = 1) { studioMediaDao.insertMedia(any()) }
        }

    @Test
    fun `event feed studio media upsert updates mediaUri when remote url is available`() =
        runTest {
            val mediaId = "media-1"
            val localPath = "file:///data/user/0/com.browntowndev.enclave/files/studio_123.jpg"
            val remoteUrl = "https://firebasestorage.googleapis.com/v0/b/bucket/o/media-1"
            val studioMediaDao = mockk<com.browntowndev.enclave.core.data.local.StudioMediaDao>(relaxed = true)
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
            val captured = captureStudioMediaUpserts(studioMediaDao)
            val cryptoManager = mockk<CryptoManagerPort>(relaxed = true)
            val (applier, sourceDocuments) =
                buildStudioMediaHydrationApplier(
                    studioMediaDao = studioMediaDao,
                    cryptoManager = cryptoManager,
                )
            sourceDocuments.put(
                "users/alice/studio_gallery/$mediaId",
                mapOf(
                    "prompt" to Blob.fromBytes("A sunset".toByteArray()),
                    "media_type" to "IMAGE",
                    "created_at" to 1000L,
                    "updated_at" to 2000L,
                    "deleted" to false,
                    "album_id" to null,
                    "remote_url" to Blob.fromBytes(remoteUrl.toByteArray()),
                    "remote_thumbnail_url" to null,
                ),
            )
            applier.apply(
                userId = "alice",
                event =
                    FirestoreSyncEvent(
                        sequence = 1,
                        schemaVersion = 1,
                        operation = "upsert",
                        collectionPath = "studio_gallery",
                        entityType = "studio_gallery",
                        entityId = mediaId,
                        entityPath = "users/alice/studio_gallery/$mediaId",
                        sourceExists = true,
                        sourceUpdatedAt = 2000L,
                        sourceDeleted = false,
                        deviceId = "device-remote",
                        clientMutationId = null,
                        triggerEventId = "event-1",
                        emittedAtMillis = 1700000001001,
                    ),
            )

            assertEquals(1, captured.size)
            assertEquals(remoteUrl, captured.first().mediaUri)
            assertEquals(remoteUrl, captured.first().remoteUrl)
        }
}
