package com.browntowndev.pocketcrew.core.data.sync

import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.WorkManager
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

class CloudSyncTriggerTest {

    private val workManager = mockk<WorkManager>(relaxed = true)
    private lateinit var trigger: CloudSyncTrigger

    @BeforeEach
    fun setup() {
        trigger = CloudSyncTrigger(workManager)
    }

    @Nested
    @DisplayName("trigger")
    inner class TriggerTests {

        @Test
        @DisplayName("enqueues unique work with name pocketcrew_sync")
        fun enqueuesUniqueWorkWithCorrectName() {
            trigger.trigger()

            verify {
                workManager.enqueueUniqueWork(
                    "pocketcrew_sync",
                    ExistingWorkPolicy.REPLACE,
                    any<androidx.work.OneTimeWorkRequest>()
                )
            }
        }

        @Test
        @DisplayName("uses REPLACE policy so rapid calls debounce to a single enqueue")
        fun usesReplacePolicy() {
            val policySlot = slot<ExistingWorkPolicy>()
            val nameSlot = slot<String>()

            io.mockk.every {
                workManager.enqueueUniqueWork(capture(nameSlot), capture(policySlot), any<androidx.work.OneTimeWorkRequest>())
            } returns mockk(relaxed = true)

            trigger.trigger()
            trigger.trigger()
            trigger.trigger()

            verify(exactly = 9) { workManager.enqueueUniqueWork(any<String>(), any<ExistingWorkPolicy>(), any<androidx.work.OneTimeWorkRequest>()) }
            assertEquals(ExistingWorkPolicy.REPLACE, policySlot.captured)
        }

        @Test
        @DisplayName("requires CONNECTED network type")
        fun requiresConnectedNetwork() {
            val requestSlot = slot<androidx.work.OneTimeWorkRequest>()

            io.mockk.every {
                workManager.enqueueUniqueWork(any(), any(), capture(requestSlot))
            } returns mockk(relaxed = true)

            trigger.trigger()

            val constraints = requestSlot.captured.workSpec.constraints
            assertEquals(NetworkType.CONNECTED, constraints.requiredNetworkType)
        }
    }
}
