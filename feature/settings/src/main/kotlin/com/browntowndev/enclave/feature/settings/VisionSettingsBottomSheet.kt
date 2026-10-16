package com.browntowndev.enclave.feature.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.browntowndev.enclave.core.ui.component.assignment.AssignmentSelectionContent
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveModalBottomSheet
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveNavRow
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveSheetHeader
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.TtsProviderId
import com.browntowndev.enclave.domain.model.inference.ModelType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisionSettingsBottomSheet(
    uiState: SettingsUiState,
    onDismiss: () -> Unit,
    onSetDefaultModel: (
        ModelType,
        LocalModelConfigurationId?,
        ApiModelConfigurationId?,
        TtsProviderId?,
        com.browntowndev.enclave.domain.model.config.MediaProviderId?,
        com.browntowndev.enclave.domain.model.config.LiveVoiceProviderId?,
    ) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var isDrilledDown by remember { mutableStateOf(false) }

    EnclaveModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        AnimatedContent(
            targetState = isDrilledDown,
            label = "VisionSettingsTransition",
        ) { drilledDown ->
            if (drilledDown) {
                AssignmentSelectionContent(
                    modifier = Modifier.fillMaxWidth(),
                    slotLabel = "Vision",
                    localAssets = emptyList(),
                    apiAssets = uiState.apiProvidersSheet.assets.filter { asset -> asset.configurations.any { it.isMultimodal } },
                    ttsAssets = emptyList(),
                    liveVoiceAssets = emptyList(),
                    mediaAssets = emptyList(),
                    onDismiss = onDismiss,
                    onBack = { isDrilledDown = false },
                    onSelect = { localId, apiId, ttsId, mediaId, liveVoiceId ->
                        onSetDefaultModel(ModelType.VISION, localId, apiId, ttsId, mediaId, liveVoiceId)
                        isDrilledDown = false
                    },
                )
            } else {
                Column(modifier = Modifier.fillMaxWidth()) {
                    EnclaveSheetHeader(
                        title = "Vision Settings",
                        onDismiss = onDismiss,
                    )
                    val currentVisionModel = uiState.assignments.assignments.find { it.modelType == ModelType.VISION }
                    EnclaveNavRow(
                        title = "API Vision Model",
                        subtitle = formatVisionAssignmentSubtitle(currentVisionModel),
                        icon = Icons.Default.Visibility,
                        onClick = { isDrilledDown = true },
                    )
                }
            }
        }
    }
}

private fun formatVisionAssignmentSubtitle(assignment: DefaultModelAssignmentUi?): String {
    if (assignment == null) return "None"
    val display = assignment.assignmentRowDisplay
    return listOfNotNull(
        display.presetName.takeIf { it != "None Assigned" },
        display.providerName,
        display.modelName,
    ).joinToString(" • ").ifBlank { "None" }
}
