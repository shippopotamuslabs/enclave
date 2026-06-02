package com.browntowndev.enclave.feature.settings.steps

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.browntowndev.enclave.core.ui.component.neo.NeoPrimaryButton
import com.browntowndev.enclave.core.ui.component.neo.NeoSectionHeader
import com.browntowndev.enclave.core.ui.component.neo.NeoSelectionCard
import com.browntowndev.enclave.domain.model.config.ApiModelConfiguration
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId

@Composable
internal fun ModelSelectStep(
    models: List<ApiModelConfiguration>,
    selectedConfigId: ApiModelConfigurationId?,
    onModelSelected: (ApiModelConfigurationId) -> Unit,
    onNext: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp),
    ) {
        NeoSectionHeader(text = "Select analysis model")
        Text("Choose one of your configured API models. This model will run all four analysis passes.")
        Spacer(Modifier.height(12.dp))

        if (models.isEmpty()) {
            Text("No API models configured. Go to Settings → Models to add one.")
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(models, key = { it.id.value }) { config ->
                    val isSelected = config.id == selectedConfigId
                    NeoSelectionCard(
                        title = config.displayName.ifBlank { config.modelId },
                        subtitle = config.modelId.takeIf { config.displayName.isNotBlank() },
                        isSelected = isSelected,
                        onClick = { onModelSelected(config.id) },
                    )
                    Spacer(Modifier.height(4.dp))
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        NeoPrimaryButton(
            text = "Continue",
            onClick = onNext,
            enabled = selectedConfigId != null,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
