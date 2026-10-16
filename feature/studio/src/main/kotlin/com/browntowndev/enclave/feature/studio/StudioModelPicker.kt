package com.browntowndev.enclave.feature.studio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.browntowndev.enclave.core.ui.component.enclave.EnclavePrimaryButton
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveSecondaryButton
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveSectionHeader
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveSelectionCard

enum class StudioOverrideKind {
    LOCAL,
    API,
    MEDIA_PROVIDER,
}

@Immutable
data class StudioModelOptionUi(
    val id: String,
    val title: String,
    val subtitle: String,
    val kind: StudioOverrideKind,
    val displayName: String,
)

@Immutable
data class StudioCapablePresetsUi(
    val localAssets: List<StudioModelOptionUi> = emptyList(),
    val apiAssets: List<StudioModelOptionUi> = emptyList(),
    val mediaAssets: List<StudioModelOptionUi> = emptyList(),
) {
    val isEmpty: Boolean
        get() = localAssets.isEmpty() && apiAssets.isEmpty() && mediaAssets.isEmpty()

    companion object {
        val EMPTY = StudioCapablePresetsUi()
    }
}

/** The two top-level views swapped by the slide [androidx.compose.animation.AnimatedContent]. */
enum class StudioSheetView {
    Settings,
    ModelPicker,
}

@Composable
fun ModelPickerPane(
    assets: StudioCapablePresetsUi,
    selectedId: String?,
    modelTypeLabel: String?,
    onStageSelection: (StudioModelOptionUi) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
    ) {
        if (assets.isEmpty) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No models available for ${modelTypeLabel ?: "this type"}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            if (assets.localAssets.isNotEmpty()) {
                EnclaveSectionHeader(text = "On-Device")
                Spacer(modifier = Modifier.height(8.dp))
                assets.localAssets.forEach { option ->
                    ModelPickerRow(option, selectedId, onStageSelection)
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            if (assets.apiAssets.isNotEmpty()) {
                EnclaveSectionHeader(text = "API")
                Spacer(modifier = Modifier.height(8.dp))
                assets.apiAssets.forEach { option ->
                    ModelPickerRow(option, selectedId, onStageSelection)
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            if (assets.mediaAssets.isNotEmpty()) {
                EnclaveSectionHeader(text = "Providers")
                Spacer(modifier = Modifier.height(8.dp))
                assets.mediaAssets.forEach { option ->
                    ModelPickerRow(option, selectedId, onStageSelection)
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            EnclaveSecondaryButton(
                text = "Cancel",
                onClick = onCancel,
                modifier = Modifier.weight(1f),
            )
            EnclavePrimaryButton(
                text = "Confirm",
                onClick = onConfirm,
                enabled = selectedId != null,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun ModelPickerRow(
    option: StudioModelOptionUi,
    selectedId: String?,
    onStageSelection: (StudioModelOptionUi) -> Unit,
) {
    EnclaveSelectionCard(
        title = option.title,
        subtitle = option.subtitle,
        isSelected = option.id == selectedId,
        onClick = { onStageSelection(option) },
        modifier = Modifier.padding(vertical = 4.dp),
    )
}
