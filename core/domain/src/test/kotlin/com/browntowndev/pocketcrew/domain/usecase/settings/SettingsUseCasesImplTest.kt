package com.browntowndev.pocketcrew.domain.usecase.settings

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class SettingsUseCasesImplTest {

    @Test
    fun `root settings use cases expose grouped collaborators and getSettings shortcut`() {
        val preferences = mockk<SettingsPreferencesUseCases>()
        val localModels = mockk<SettingsLocalModelUseCases>()
        val apiProviders = mockk<SettingsApiProviderUseCases>()
        val assignments = mockk<SettingsAssignmentUseCases>()
        val deletion = mockk<SettingsDeletionUseCases>()
        val getSettings = mockk<GetSettingsUseCase>()
        every { preferences.getSettings } returns getSettings

        val useCases = SettingsUseCasesImpl(
            preferences = preferences,
            localModels = localModels,
            apiProviders = apiProviders,
            assignments = assignments,
            deletion = deletion,
        )

        assertSame(preferences, useCases.preferences)
        assertSame(localModels, useCases.localModels)
        assertSame(apiProviders, useCases.apiProviders)
        assertSame(assignments, useCases.assignments)
        assertSame(deletion, useCases.deletion)
        assertSame(getSettings, useCases.getSettings)
    }
}
