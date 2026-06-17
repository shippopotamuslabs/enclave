package com.browntowndev.enclave.feature.settings

import com.browntowndev.enclave.domain.model.download.ModelPackCompleteness
import com.browntowndev.enclave.domain.usecase.modelconfig.ModelPackSettingsRow
import javax.inject.Inject

class ModelPackUiMapper
    @Inject
    constructor() {
        fun map(row: ModelPackSettingsRow): ModelPackUi =
            ModelPackUi(
                packId = row.pack.packId,
                displayName = row.pack.displayName,
                completenessLabel = completenessLabel(row.completeness),
                installedCount = row.completeness.installedCount,
                totalCount = row.completeness.totalCount,
                showDownloadAction = row.showDownloadAction,
                isDiffusionSoftDeleted = row.isDiffusionSoftDeleted,
                disabledReason = row.disabledReason,
                isEligible = row.isEligible,
            )

        fun completenessLabel(completeness: ModelPackCompleteness): String = "${completeness.installedCount}/${completeness.totalCount}"
    }
