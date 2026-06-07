package com.browntowndev.enclave.presentation.navigation

import com.browntowndev.enclave.domain.model.download.DownloadModelsResult
import com.browntowndev.enclave.domain.model.download.LocalImagePackMemberFilter
/**
 * Filters startup download results when the user completes onboarding with API model choice.
 * Must exclude entire IMAGE_GENERATION and IMAGE_EDIT catalog packs (slot + companion utilities).
 */
fun filterDownloadModelsResultForApiOnboarding(result: DownloadModelsResult): DownloadModelsResult =
    LocalImagePackMemberFilter.excludeForApiOnboarding(result)
