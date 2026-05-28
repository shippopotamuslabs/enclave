package com.browntowndev.enclave.domain.util

import com.browntowndev.enclave.domain.model.settings.SearchProvider
import com.browntowndev.enclave.domain.port.repository.SettingsData

/**
 * Whether search-related tools are configured (toggle on and API key present).
 * Used so the model is never told a search backend exists when it cannot run.
 */
fun SettingsData.isWebSearchSkillConfigured(): Boolean {
    if (!searchEnabled) return false
    return when (searchProvider) {
        SearchProvider.TAVILY -> tavilyKeyPresent
        SearchProvider.EXA -> exaKeyPresent
    }
}

fun SettingsData.isXSearchSkillConfigured(): Boolean = xSearchEnabled && xApiKeyPresent
