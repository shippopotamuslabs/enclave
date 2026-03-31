package com.browntowndev.pocketcrew.domain.usecase.settings

import com.browntowndev.pocketcrew.domain.model.settings.AppTheme
import com.browntowndev.pocketcrew.domain.usecase.FakeSettingsRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class UpdateThemeUseCaseTest {

    private lateinit var fakeRepository: FakeSettingsRepository
    private lateinit var updateThemeUseCase: UpdateThemeUseCase

    @BeforeEach
    fun setup() {
        fakeRepository = FakeSettingsRepository()
        updateThemeUseCase = UpdateThemeUseCase(fakeRepository)
    }

    @Test
    fun `invoke updates theme to SYSTEM`() = runTest {
        // When
        updateThemeUseCase(AppTheme.SYSTEM)

        // Then
        fakeRepository.verifyUpdateThemeCalled(1, AppTheme.SYSTEM)
    }

    @Test
    fun `invoke updates theme to DYNAMIC`() = runTest {
        // When
        updateThemeUseCase(AppTheme.DYNAMIC)

        // Then
        fakeRepository.verifyUpdateThemeCalled(1, AppTheme.DYNAMIC)
    }

    @Test
    fun `invoke updates theme to DARK`() = runTest {
        // When
        updateThemeUseCase(AppTheme.DARK)

        // Then
        fakeRepository.verifyUpdateThemeCalled(1, AppTheme.DARK)
    }

    @Test
    fun `invoke updates theme to LIGHT`() = runTest {
        // When
        updateThemeUseCase(AppTheme.LIGHT)

        // Then
        fakeRepository.verifyUpdateThemeCalled(1, AppTheme.LIGHT)
    }

    @Test
    fun `invoke can update theme multiple times`() = runTest {
        // When
        updateThemeUseCase(AppTheme.DARK)
        updateThemeUseCase(AppTheme.LIGHT)
        updateThemeUseCase(AppTheme.SYSTEM)

        // Then
        fakeRepository.verifyUpdateThemeCalled(3, AppTheme.SYSTEM)
    }

    @Test
    fun `invoke throws exception when repository fails`() = runTest {
        // Given
        fakeRepository.shouldThrowOnUpdateTheme = true

        // When/Then
        assertThrows(RuntimeException::class.java) {
            runTest { updateThemeUseCase(AppTheme.DARK) }
        }
    }
}

