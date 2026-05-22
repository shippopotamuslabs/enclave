package com.browntowndev.enclave.presentation.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BiometricPromptCoordinatorTest {
    @Test
    fun tryStartPrompt_enabledAndIdle_startsPrompt() {
        val coordinator = BiometricPromptCoordinator()
        var promptCount = 0

        coordinator.tryStartPrompt(
            isEnabled = true,
            isUnlocking = false,
        ) {
            promptCount += 1
        }

        assertEquals(1, promptCount)
    }

    @Test
    fun tryStartPrompt_promptInFlight_ignoresDuplicateResume() {
        val coordinator = BiometricPromptCoordinator()
        var promptCount = 0

        coordinator.tryStartPrompt(
            isEnabled = true,
            isUnlocking = false,
        ) {
            promptCount += 1
        }
        coordinator.tryStartPrompt(
            isEnabled = true,
            isUnlocking = false,
        ) {
            promptCount += 1
        }

        assertEquals(1, promptCount)
    }

    @Test
    fun tryStartPrompt_afterPromptFinishes_allowsResumeRetry() {
        val coordinator = BiometricPromptCoordinator()
        var promptCount = 0

        coordinator.tryStartPrompt(
            isEnabled = true,
            isUnlocking = false,
        ) {
            promptCount += 1
        }
        coordinator.finishPrompt()
        coordinator.tryStartPrompt(
            isEnabled = true,
            isUnlocking = false,
        ) {
            promptCount += 1
        }

        assertEquals(2, promptCount)
    }

    @Test
    fun tryStartPrompt_disabledOrUnlocking_doesNotStartPrompt() {
        val coordinator = BiometricPromptCoordinator()
        var promptCount = 0

        coordinator.tryStartPrompt(
            isEnabled = false,
            isUnlocking = false,
        ) {
            promptCount += 1
        }
        coordinator.tryStartPrompt(
            isEnabled = true,
            isUnlocking = true,
        ) {
            promptCount += 1
        }

        assertEquals(0, promptCount)
    }

    @Test
    fun tryStartUserPrompt_clearsStaleInFlightLockAndRetries() {
        val coordinator = BiometricPromptCoordinator()
        var promptCount = 0

        coordinator.tryStartPrompt(
            isEnabled = true,
            isUnlocking = false,
        ) {
            promptCount += 1
        }
        coordinator.tryStartUserPrompt(isEnabled = true) {
            promptCount += 1
        }

        assertEquals(2, promptCount)
    }

    @Test
    fun tryStartUserPrompt_ignoresUnlockingGate() {
        val coordinator = BiometricPromptCoordinator()
        var promptCount = 0

        coordinator.tryStartUserPrompt(isEnabled = true) {
            promptCount += 1
        }

        assertEquals(1, promptCount)
    }
}
