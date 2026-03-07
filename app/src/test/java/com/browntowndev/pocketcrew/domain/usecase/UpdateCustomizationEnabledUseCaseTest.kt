package com.browntowndev.pocketcrew.domain.usecase

import com.browntowndev.pocketcrew.domain.usecase.settings.UpdateCustomizationEnabledUseCase
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class UpdateCustomizationEnabledUseCaseTest {

    private lateinit var fakeRepository: FakeSettingsRepository
    private lateinit var updateCustomizationEnabledUseCase: UpdateCustomizationEnabledUseCase

    @BeforeEach
    fun setup() {
        fakeRepository = FakeSettingsRepository()
        updateCustomizationEnabledUseCase = UpdateCustomizationEnabledUseCase(fakeRepository)
    }

    @Test
    fun `invoke updates customization enabled to true`() = runTest {
        // When
        updateCustomizationEnabledUseCase(true)

        // Then
        fakeRepository.verifyUpdateCustomizationEnabledCalled(1, true)
    }

    @Test
    fun `invoke updates customization enabled to false`() = runTest {
        // When
        updateCustomizationEnabledUseCase(false)

        // Then
        fakeRepository.verifyUpdateCustomizationEnabledCalled(1, false)
    }

    @Test
    fun `invoke can toggle customization enabled multiple times`() = runTest {
        // When
        updateCustomizationEnabledUseCase(false)
        updateCustomizationEnabledUseCase(true)
        updateCustomizationEnabledUseCase(false)

        // Then
        fakeRepository.verifyUpdateCustomizationEnabledCalled(3, false)
    }

    @Test
    fun `invoke throws exception when repository fails`() = runTest {
        // Given
        fakeRepository.shouldThrowOnUpdateCustomizationEnabled = true

        // When/Then
        assertThrows(RuntimeException::class.java) {
            runTest { updateCustomizationEnabledUseCase(true) }
        }
    }
}

