package com.browntowndev.enclave.domain.usecase

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FakeSettingsRepositoryXSearchTest {
    @Test
    fun `initial X Search state is disabled`() =
        runTest {
            val repository = FakeSettingsRepository()
            repository.settingsFlow.test {
                val settings = awaitItem()
                // Assuming xSearchEnabled and xApiKeyPresent exist in SettingsData
                assertFalse(settings.xSearchEnabled)
                assertFalse(settings.xApiKeyPresent)
            }
        }

    @Test
    fun `updateXSearchEnabled updates the flow`() =
        runTest {
            val repository = FakeSettingsRepository()
            repository.settingsFlow.test {
                awaitItem() // Initial

                repository.updateXSearchEnabled(true)
                assertTrue(awaitItem().xSearchEnabled)

                repository.updateXSearchEnabled(false)
                assertFalse(awaitItem().xSearchEnabled)
            }
        }

    @Test
    fun `saveXApiKey updates xApiKeyPresent in the flow`() =
        runTest {
            val repository = FakeSettingsRepository()
            repository.settingsFlow.test {
                awaitItem() // Initial

                repository.saveXApiKey("test-key")
                assertTrue(awaitItem().xApiKeyPresent)

                repository.clearXApiKey()
                assertFalse(awaitItem().xApiKeyPresent)
            }
        }
}
