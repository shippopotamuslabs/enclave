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
import com.browntowndev.enclave.core.ui.component.neo.NeoModalBottomSheet
import com.browntowndev.enclave.core.ui.component.neo.NeoNavRow
import com.browntowndev.enclave.core.ui.component.neo.NeoSheetHeader
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
    ) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var isDrilledDown by remember { mutableStateOf(false) }

    NeoModalBottomSheet(
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
                    mediaAssets = emptyList(),
                    onDismiss = onDismiss,
                    onBack = { isDrilledDown = false },
                    onSelect = { localId, apiId, ttsId, mediaId ->
                        onSetDefaultModel(ModelType.VISION, localId, apiId, ttsId, mediaId)
                        isDrilledDown = false
                    },
                )
            } else {
                Column(modifier = Modifier.fillMaxWidth()) {
                    NeoSheetHeader(
                        title = "Vision Settings",
                        onDismiss = onDismiss,
                    )
                    val currentVisionModel = uiState.assignments.assignments.find { it.modelType == ModelType.VISION }
                    NeoNavRow(
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
    val provider = assignment.providerName
    val modelLabel =
        assignment.modelId?.takeIf { it.isNotBlank() }
            ?: assignment.presetName
            ?: assignment.currentModelName.takeIf { it != "None Assigned" }
    return when {
        provider != null && modelLabel != null -> "$provider • $modelLabel"
        provider != null -> provider
        modelLabel != null -> modelLabel
        else -> "None"
    }
}
