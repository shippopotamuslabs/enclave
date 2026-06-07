package com.browntowndev.enclave.domain.model.download

import com.browntowndev.enclave.domain.model.config.LocalModelAsset
import com.browntowndev.enclave.domain.model.inference.ModelType

/**
 * Result of checkModels containing both the scan result and models that need downloading.
 * This eliminates duplicate scanning in startDownloads.
 */
data class DownloadModelsResult(
    val allModels: Map<ModelType, LocalModelAsset>,
    val utilityAssets: List<LocalModelAsset> = emptyList(),
    val modelsToDownload: List<LocalModelAsset>,
    val scanResult: ModelScanResult,
    /**
     * Models that were previously downloaded but soft-deleted by the user.
     * These are available for re-download and should be shown in the UI
     * as "Available for Download" (separate from "Needs Download").
     *
     * A soft-deleted model is identified by: LocalModelEntity exists but has 0 configs.
     * The model is NOT passed to CheckModelsUseCase for file scanning because
     * the missing file would trigger an unwanted re-download.
     */
    val availableToRedownload: List<LocalModelAsset> = emptyList(),
    /**
     * SHA-256 hashes of all members in [ModelType.IMAGE_GENERATION] catalog packs.
     * Populated at init from `modelPacks[]` so API onboarding can exclude the full pack without hardcoded policy.
     */
    val imageGenerationPackMemberSha256s: Set<String> = emptySet(),
    /**
     * SHA-256 hashes of all members in [ModelType.IMAGE_EDIT] catalog packs.
     */
    val imageEditPackMemberSha256s: Set<String> = emptySet(),
)
