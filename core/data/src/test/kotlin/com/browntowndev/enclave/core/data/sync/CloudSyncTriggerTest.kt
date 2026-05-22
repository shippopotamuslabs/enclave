package com.browntowndev.enclave.core.data.sync

import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class CloudSyncTriggerTest {
    private val workManager = mockk<WorkManager>(relaxed = true)
    private val loggingPort = mockk<LoggingPort>(relaxed = true)
    private lateinit var trigger: CloudSyncTrigger

    @BeforeEach
    fun setup() {
        trigger = CloudSyncTrigger(workManager, loggingPort)
    }

    @Nested
    @DisplayName("trigger")
    inner class TriggerTests {
        @Test
        @DisplayName("enqueues unique work with correct names")
        fun enqueuesUniqueWorkWithCorrectNames() {
            trigger.trigger()

            verify {
                workManager.enqueueUniqueWork(
                    "enclave_sync",
                    ExistingWorkPolicy.APPEND_OR_REPLACE,
                    any<androidx.work.OneTimeWorkRequest>(),
                )
                workManager.enqueueUniqueWork("enclave_media_sync", ExistingWorkPolicy.REPLACE, any<androidx.work.OneTimeWorkRequest>())
                workManager.enqueueUniqueWork("enclave_file_sync", ExistingWorkPolicy.REPLACE, any<androidx.work.OneTimeWorkRequest>())
            }
        }
    }

    @Test
    @DisplayName("triggerMetadata uses APPEND_OR_REPLACE policy")
    fun triggerMetadataUsesAppendOrReplace() {
        trigger.triggerMetadata()
        verify {
            workManager.enqueueUniqueWork(
                "enclave_sync",
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                any<androidx.work.OneTimeWorkRequest>(),
            )
        }
    }

    @Test
    @DisplayName("triggerMedia uses REPLACE policy")
    fun triggerMediaUsesReplace() {
        trigger.triggerMedia()
        verify {
            workManager.enqueueUniqueWork(
                "enclave_media_sync",
                ExistingWorkPolicy.REPLACE,
                any<androidx.work.OneTimeWorkRequest>(),
            )
        }
    }

    @Test
    @DisplayName("triggerFiles uses REPLACE policy")
    fun triggerFilesUsesReplace() {
        trigger.triggerFiles()
        verify {
            workManager.enqueueUniqueWork(
                "enclave_file_sync",
                ExistingWorkPolicy.REPLACE,
                any<androidx.work.OneTimeWorkRequest>(),
            )
        }
    }
}
