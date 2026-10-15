package com.browntowndev.enclave.core.testing.theme

import com.browntowndev.enclave.domain.theme.ColorMode
import com.browntowndev.enclave.domain.theme.ThemeFamily
import com.browntowndev.enclave.domain.theme.ThemePreferences
import com.browntowndev.enclave.domain.theme.ThemePreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class FakeThemePreferencesRepository(
    initial: ThemePreferences = ThemePreferences(),
) : ThemePreferencesRepository {
    private val state = MutableStateFlow(initial)

    override fun observe(): Flow<ThemePreferences> = state.asStateFlow()

    override suspend fun setFamily(family: ThemeFamily) {
        state.update { it.copy(family = family) }
    }

    override suspend fun setColorMode(colorMode: ColorMode) {
        state.update { it.copy(colorMode = colorMode) }
    }

    fun seed(preferences: ThemePreferences) {
        state.value = preferences
    }
}
