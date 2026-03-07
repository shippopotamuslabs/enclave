package com.browntowndev.pocketcrew.domain.usecase

import com.browntowndev.pocketcrew.domain.usecase.settings.UpdateAllowMemoriesUseCase
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class UpdateAllowMemoriesUseCaseTest {

    private lateinit var fakeRepository: FakeSettingsRepository
    private lateinit var updateAllowMemoriesUseCase: UpdateAllowMemoriesUseCase

    @BeforeEach
    fun setup() {
        fakeRepository = FakeSettingsRepository()
        updateAllowMemoriesUseCase = UpdateAllowMemoriesUseCase(fakeRepository)
    }

    @Test
    fun `invoke updates allow memories to true`() = runTest {
        // When
        updateAllowMemoriesUseCase(true)

        // Then
        fakeRepository.verifyUpdateAllowMemoriesCalled(1, true)
    }

    @Test
    fun `invoke updates allow memories to false`() = runTest {
        // When
        updateAllowMemoriesUseCase(false)

        // Then
        fakeRepository.verifyUpdateAllowMemoriesCalled(1, false)
    }

    @Test
    fun `invoke can toggle allow memories multiple times`() = runTest {
        // When
        updateAllowMemoriesUseCase(false)
        updateAllowMemoriesUseCase(true)
        updateAllowMemoriesUseCase(false)

        // Then
        fakeRepository.verifyUpdateAllowMemoriesCalled(3, false)
    }

    @Test
    fun `invoke throws exception when repository fails`() = runTest {
        // Given
        fakeRepository.shouldThrowOnUpdateAllowMemories = true

        // When/Then
        assertThrows(RuntimeException::class.java) {
            runTest { updateAllowMemoriesUseCase(true) }
        }
    }
}

