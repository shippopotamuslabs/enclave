package com.browntowndev.enclave.domain.usecase.settings

import com.browntowndev.enclave.domain.usecase.FakeSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class UpdateAutomaticErrorTrackingEnabledUseCaseTest {
    private lateinit var fakeRepository: FakeSettingsRepository
    private lateinit var useCase: UpdateAutomaticErrorTrackingEnabledUseCase

    @BeforeEach
    fun setup() {
        fakeRepository = FakeSettingsRepository()
        useCase = UpdateAutomaticErrorTrackingEnabledUseCase(fakeRepository)
    }

    @Test
    fun `invoke persists automatic error tracking preference`() =
        runTest {
            useCase(true)
            assertTrue(fakeRepository.settingsFlow.first().automaticErrorTrackingEnabled)
        }
}
