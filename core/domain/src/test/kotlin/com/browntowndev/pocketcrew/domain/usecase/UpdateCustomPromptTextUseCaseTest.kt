package com.browntowndev.pocketcrew.domain.usecase

import com.browntowndev.pocketcrew.domain.usecase.settings.UpdateCustomPromptTextUseCase
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class UpdateCustomPromptTextUseCaseTest {

    private lateinit var fakeRepository: FakeSettingsRepository
    private lateinit var updateCustomPromptTextUseCase: UpdateCustomPromptTextUseCase

    @BeforeEach
    fun setup() {
        fakeRepository = FakeSettingsRepository()
        updateCustomPromptTextUseCase = UpdateCustomPromptTextUseCase(fakeRepository)
    }

    @Test
    fun `invoke updates custom prompt text with non-empty string`() = runTest {
        // When
        updateCustomPromptTextUseCase("My custom prompt")

        // Then
        fakeRepository.verifyUpdateCustomPromptTextCalled(1, "My custom prompt")
    }

    @Test
    fun `invoke updates custom prompt text with empty string`() = runTest {
        // When
        updateCustomPromptTextUseCase("")

        // Then
        fakeRepository.verifyUpdateCustomPromptTextCalled(1, "")
    }

    @Test
    fun `invoke updates custom prompt text with special characters`() = runTest {
        // When
        updateCustomPromptTextUseCase("Special chars: !@#\$%^&*()_+-=[]{}|;':\",./<>?")

        // Then
        fakeRepository.verifyUpdateCustomPromptTextCalled(1, "Special chars: !@#\$%^&*()_+-=[]{}|;':\",./<>?")
    }

    @Test
    fun `invoke updates custom prompt text with multiline text`() = runTest {
        // Given
        val multilineText = "Line 1\nLine 2\nLine 3"

        // When
        updateCustomPromptTextUseCase(multilineText)

        // Then
        fakeRepository.verifyUpdateCustomPromptTextCalled(1, multilineText)
    }

    @Test
    fun `invoke can update custom prompt text multiple times`() = runTest {
        // When
        updateCustomPromptTextUseCase("First text")
        updateCustomPromptTextUseCase("Second text")
        updateCustomPromptTextUseCase("Third text")

        // Then
        fakeRepository.verifyUpdateCustomPromptTextCalled(3, "Third text")
    }

    @Test
    fun `invoke throws exception when repository fails`() = runTest {
        // Given
        fakeRepository.shouldThrowOnUpdateCustomPromptText = true

        // When/Then
        assertThrows(RuntimeException::class.java) {
            runTest { updateCustomPromptTextUseCase("Some text") }
        }
    }
}

