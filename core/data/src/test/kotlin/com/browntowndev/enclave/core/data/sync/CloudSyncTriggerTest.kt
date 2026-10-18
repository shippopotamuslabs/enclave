package com.browntowndev.enclave.core.data.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class CloudSyncTriggerTest {
    private lateinit var workManager: WorkManager
    private lateinit var trigger: CloudSyncTrigger

    @BeforeEach
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Real WorkManager with BOTH executors synchronous (the DownloadWorkScheduler /
        // TestDoubles house pattern): constraint-bearing work stays ENQUEUED under the
        // test driver until constraints are forced met, so unique-work policy
        // resolution is observable as WorkInfo state. A no-op main executor
        // (Executor { }) must NOT be used here: WorkManager posts internal
        // state/constraint delivery on its main-thread executor, and dropping those
        // posts lets enqueued work start (and fail) mid-test, corrupting the states
        // these tests assert on.
        val config =
            Configuration
                .Builder()
                .setTaskExecutor(androidx.work.impl.utils.SynchronousExecutor())
                .setExecutor(androidx.work.impl.utils.SynchronousExecutor())
                .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        workManager = WorkManager.getInstance(context)
        trigger = CloudSyncTrigger(workManager, FakeLoggingPort())
    }

    private fun uniqueWork(name: String): List<WorkInfo> = workManager.getWorkInfosForUniqueWork(name).get()

    @Nested
    @DisplayName("trigger")
    inner class TriggerTests {
        @Test
        @DisplayName("enqueues unique work with correct names")
        fun enqueuesUniqueWorkWithCorrectNames() {
            trigger.trigger()

            val sync = uniqueWork("enclave_sync")
            assertEquals(1, sync.size)
            assertTrue(sync.single().tags.contains(SyncWorker::class.java.name))

            val media = uniqueWork("enclave_media_sync")
            assertEquals(1, media.size)
            assertTrue(media.single().tags.contains(MediaCloudSyncWorker::class.java.name))

            val files = uniqueWork("enclave_file_sync")
            assertEquals(1, files.size)
            assertTrue(files.single().tags.contains(MessageFileCloudSyncWorker::class.java.name))
        }
    }

    @Test
    @DisplayName("triggerMetadata uses APPEND_OR_REPLACE policy")
    fun triggerMetadataUsesAppendOrReplace() {
        trigger.triggerMetadata()
        trigger.triggerMetadata()

        // APPEND_OR_REPLACE appends to the in-flight chain instead of cancelling it:
        // the first request stays ENQUEUED and the second is BLOCKED behind it.
        // (Selected by state, not by index: getWorkInfosForUniqueWork has no
        // ORDER BY — WorkSpecDao's query probes by primary key — so the list order
        // is not insertion order against a real WorkManager.)
        val infos = uniqueWork("enclave_sync")
        assertEquals(2, infos.size)
        assertEquals(
            setOf(WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED),
            infos.map { it.state }.toSet(),
        )
    }

    @Test
    @DisplayName("triggerMedia uses REPLACE policy")
    fun triggerMediaUsesReplace() {
        trigger.triggerMedia()
        val first = uniqueWork("enclave_media_sync").single()
        assertEquals(WorkInfo.State.ENQUEUED, first.state)

        trigger.triggerMedia()

        // REPLACE cancels the previous pending request and enqueues a fresh one.
        // Against a REAL WorkManager the cancelled predecessor is not observable
        // after the second enqueue: EnqueueRunnable deletes the superseded
        // WorkSpec row in the same operation (the FakeWorkManager-era expectation
        // of [CANCELLED, ENQUEUED] cannot occur). REPLACE is therefore asserted in
        // its observable form: exactly one record remains, it is ENQUEUED, and it
        // is a different request than the first (replaced, not kept/coalesced).
        val infos = uniqueWork("enclave_media_sync")
        assertEquals(1, infos.size)
        assertEquals(WorkInfo.State.ENQUEUED, infos.single().state)
        assertTrue(infos.single().id != first.id)
    }

    @Test
    @DisplayName("triggerFiles uses REPLACE policy")
    fun triggerFilesUsesReplace() {
        trigger.triggerFiles()
        val first = uniqueWork("enclave_file_sync").single()
        assertEquals(WorkInfo.State.ENQUEUED, first.state)

        trigger.triggerFiles()

        // See triggerMediaUsesReplace: REPLACE's cancelled predecessor is pruned
        // by the same enqueue, so assert the observable form (one ENQUEUED
        // record with a fresh id).
        val infos = uniqueWork("enclave_file_sync")
        assertEquals(1, infos.size)
        assertEquals(WorkInfo.State.ENQUEUED, infos.single().state)
        assertTrue(infos.single().id != first.id)
    }
}
