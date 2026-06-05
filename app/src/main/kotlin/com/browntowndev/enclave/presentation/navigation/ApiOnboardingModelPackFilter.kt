package com.browntowndev.enclave.presentation.navigation

import com.browntowndev.enclave.domain.model.download.DownloadModelsResult
import com.browntowndev.enclave.domain.model.download.ImageGenerationPackMemberFilter

/**
 * Filters startup download results when the user completes onboarding with API model choice.
 * Must exclude entire IMAGE_GENERATION catalog packs (slot + companion utilities), not whitelist utilities.
 */
fun filterDownloadModelsResultForApiOnboarding(result: DownloadModelsResult): DownloadModelsResult =
    ImageGenerationPackMemberFilter.excludeFromDownloadResult(result).copy(allModels = emptyMap())
