package com.browntowndev.enclave.domain.theme

import com.browntowndev.enclave.core.testing.theme.FakeThemePreferencesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ThemePreferencesUseCasesTest {
    @Test
    fun observeEmitsDefaults_thenWritesFlowThrough() = runTest {
        val fake = FakeThemePreferencesRepository()
        val observe = ObserveThemePreferencesUseCase(fake)

        assertEquals(ThemePreferences(), observe().first())

        SetThemeFamilyUseCase(fake)(ThemeFamily.Tonal)
        SetThemeColorModeUseCase(fake)(ColorMode.Dark)

        assertEquals(
            ThemePreferences(ThemeFamily.Tonal, ColorMode.Dark),
            observe().first(),
        )
    }
}
