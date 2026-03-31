package com.browntowndev.pocketcrew.domain.usecase.settings

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import com.browntowndev.pocketcrew.domain.usecase.FakeSettingsRepository
import org.junit.jupiter.api.Assertions.*

class UpdateHapticResponseUseCaseTest {

    private lateinit var fakeRepository: FakeSettingsRepository
    private lateinit var updateHapticResponseUseCase: UpdateHapticResponseUseCase

    @BeforeEach
    fun setup() {
        fakeRepository = FakeSettingsRepository()
        updateHapticResponseUseCase = UpdateHapticResponseUseCase(fakeRepository)
    }

    @Test
    fun `invoke updates haptic response to true`() = runTest {
        // When
        updateHapticResponseUseCase(true)

        // Then
        fakeRepository.verifyUpdateHapticResponseCalled(1, true)
    }

    @Test
    fun `invoke updates haptic response to false`() = runTest {
        // When
        updateHapticResponseUseCase(false)

        // Then
        fakeRepository.verifyUpdateHapticResponseCalled(1, false)
    }

    @Test
    fun `invoke can toggle haptic response multiple times`() = runTest {
        // When
        updateHapticResponseUseCase(false)
        updateHapticResponseUseCase(true)
        updateHapticResponseUseCase(false)

        // Then
        fakeRepository.verifyUpdateHapticResponseCalled(3, false)
    }

    @Test
    fun `invoke throws exception when repository fails`() = runTest {
        // Given
        fakeRepository.shouldThrowOnUpdateHapticResponse = true

        // When/Then
        assertThrows(RuntimeException::class.java) {
            runTest { updateHapticResponseUseCase(true) }
        }
    }
}

