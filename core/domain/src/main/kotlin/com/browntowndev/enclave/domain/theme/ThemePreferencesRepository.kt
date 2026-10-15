package com.browntowndev.enclave.domain.theme

import kotlinx.coroutines.flow.Flow

interface ThemePreferencesRepository {
    fun observe(): Flow<ThemePreferences>

    suspend fun setFamily(family: ThemeFamily)

    suspend fun setColorMode(colorMode: ColorMode)
}
