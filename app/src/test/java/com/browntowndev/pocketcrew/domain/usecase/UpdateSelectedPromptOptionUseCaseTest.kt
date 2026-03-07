package com.browntowndev.pocketcrew.domain.usecase

import com.browntowndev.pocketcrew.domain.model.SystemPromptOption
import com.browntowndev.pocketcrew.domain.usecase.settings.UpdateSelectedPromptOptionUseCase
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class UpdateSelectedPromptOptionUseCaseTest {

    private lateinit var fakeRepository: FakeSettingsRepository
    private lateinit var updateSelectedPromptOptionUseCase: UpdateSelectedPromptOptionUseCase

    @BeforeEach
    fun setup() {
        fakeRepository = FakeSettingsRepository()
        updateSelectedPromptOptionUseCase = UpdateSelectedPromptOptionUseCase(fakeRepository)
    }

    @Test
    fun `invoke updates selected prompt option to CUSTOM`() = runTest {
        // When
        updateSelectedPromptOptionUseCase(SystemPromptOption.CUSTOM)

        // Then
        fakeRepository.verifyUpdateSelectedPromptOptionCalled(1, SystemPromptOption.CUSTOM)
    }

    @Test
    fun `invoke updates selected prompt option to CONCISE`() = runTest {
        // When
        updateSelectedPromptOptionUseCase(SystemPromptOption.CONCISE)

        // Then
        fakeRepository.verifyUpdateSelectedPromptOptionCalled(1, SystemPromptOption.CONCISE)
    }

    @Test
    fun `invoke updates selected prompt option to FORMAL`() = runTest {
        // When
        updateSelectedPromptOptionUseCase(SystemPromptOption.FORMAL)

        // Then
        fakeRepository.verifyUpdateSelectedPromptOptionCalled(1, SystemPromptOption.FORMAL)
    }

    @Test
    fun `invoke updates selected prompt option to RIGOROUS`() = runTest {
        // When
        updateSelectedPromptOptionUseCase(SystemPromptOption.RIGOROUS)

        // Then
        fakeRepository.verifyUpdateSelectedPromptOptionCalled(1, SystemPromptOption.RIGOROUS)
    }

    @Test
    fun `invoke can change prompt option multiple times`() = runTest {
        // When
        updateSelectedPromptOptionUseCase(SystemPromptOption.CUSTOM)
        updateSelectedPromptOptionUseCase(SystemPromptOption.RIGOROUS)
        updateSelectedPromptOptionUseCase(SystemPromptOption.FORMAL)

        // Then
        fakeRepository.verifyUpdateSelectedPromptOptionCalled(3, SystemPromptOption.FORMAL)
    }

    @Test
    fun `invoke throws exception when repository fails`() = runTest {
        // Given
        fakeRepository.shouldThrowOnUpdateSelectedPromptOption = true

        // When/Then
        assertThrows(RuntimeException::class.java) {
            runTest { updateSelectedPromptOptionUseCase(SystemPromptOption.CONCISE) }
        }
    }
}

