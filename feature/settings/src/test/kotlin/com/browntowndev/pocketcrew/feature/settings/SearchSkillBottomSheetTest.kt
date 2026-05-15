package com.browntowndev.pocketcrew.feature.settings

import com.browntowndev.pocketcrew.domain.model.settings.SearchProvider
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SearchSkillBottomSheetTest {

    @Test
    fun `shouldShowProviderSwitchAlert returns true when leaving Tavily with saved key`() {
        val state = SearchSkillEditorUiState(
            selectedProvider = SearchProvider.TAVILY,
            tavilyKeyPresent = true,
        )
        assertTrue(shouldShowProviderSwitchAlert(state, SearchProvider.EXA))
    }

    @Test
    fun `shouldShowProviderSwitchAlert returns false when leaving Tavily without saved key`() {
        val state = SearchSkillEditorUiState(
            selectedProvider = SearchProvider.TAVILY,
            tavilyKeyPresent = false,
        )
        assertFalse(shouldShowProviderSwitchAlert(state, SearchProvider.EXA))
    }

    @Test
    fun `shouldShowProviderSwitchAlert returns true when leaving Exa with saved key`() {
        val state = SearchSkillEditorUiState(
            selectedProvider = SearchProvider.EXA,
            exaKeyPresent = true,
        )
        assertTrue(shouldShowProviderSwitchAlert(state, SearchProvider.TAVILY))
    }

    @Test
    fun `shouldShowProviderSwitchAlert returns false when leaving Exa without saved key`() {
        val state = SearchSkillEditorUiState(
            selectedProvider = SearchProvider.EXA,
            exaKeyPresent = false,
        )
        assertFalse(shouldShowProviderSwitchAlert(state, SearchProvider.TAVILY))
    }
}
