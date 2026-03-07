package com.browntowndev.pocketcrew.domain.usecase

import com.browntowndev.pocketcrew.domain.usecase.settings.UpdateHapticPressUseCase
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class UpdateHapticPressUseCaseTest {

    private lateinit var fakeRepository: FakeSettingsRepository
    private lateinit var updateHapticPressUseCase: UpdateHapticPressUseCase

    @BeforeEach
    fun setup() {
        fakeRepository = FakeSettingsRepository()
        updateHapticPressUseCase = UpdateHapticPressUseCase(fakeRepository)
    }

    @Test
    fun `invoke updates haptic press to true`() = runTest {
        // When
        updateHapticPressUseCase(true)

        // Then
        fakeRepository.verifyUpdateHapticPressCalled(1, true)
    }

    @Test
    fun `invoke updates haptic press to false`() = runTest {
        // When
        updateHapticPressUseCase(false)

        // Then
        fakeRepository.verifyUpdateHapticPressCalled(1, false)
    }

    @Test
    fun `invoke can toggle haptic press multiple times`() = runTest {
        // When
        updateHapticPressUseCase(false)
        updateHapticPressUseCase(true)
        updateHapticPressUseCase(false)

        // Then
        fakeRepository.verifyUpdateHapticPressCalled(3, false)
    }

    @Test
    fun `invoke throws exception when repository fails`() = runTest {
        // Given
        fakeRepository.shouldThrowOnUpdateHapticPress = true

        // When/Then
        assertThrows(RuntimeException::class.java) {
            runTest { updateHapticPressUseCase(true) }
        }
    }
}

