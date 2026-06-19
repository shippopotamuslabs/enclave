package com.browntowndev.enclave.presentation.navigation

import com.browntowndev.enclave.domain.model.download.DownloadModelsResult
import com.browntowndev.enclave.domain.model.download.LocalImagePackMemberFilter
import com.browntowndev.enclave.domain.model.inference.ModelType

/**
 * Filters startup download results when the user completes onboarding with API model choice.
 * Excludes LLMs and optionally image generation models if they are configured as API.
 */
fun filterDownloadModelsResultForApiOnboarding(
    result: DownloadModelsResult,
    apiSlots: Set<ModelType>,
): DownloadModelsResult =
    LocalImagePackMemberFilter.excludeForApiOnboarding(result, apiSlots)
