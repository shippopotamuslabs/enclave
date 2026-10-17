package com.browntowndev.enclave.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveOutlinedTextField
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveTelemetryMetricPanel
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveTelemetryMetricRow
import com.browntowndev.enclave.domain.model.config.ApiModelPricingFormat

@Composable
fun ApiModelPricingConfiguration(
    provider: ApiProviderUi,
    config: ApiModelConfigUi,
    selectedModelMetadata: DiscoveredApiModelUi?,
    onConfigChange: (ApiModelConfigUi) -> Unit,
) {
    val usesDiscoveryPricing = provider.exposesDiscoveryModelPricing
    val helperText =
        if (usesDiscoveryPricing) {
            "Rates come from ${provider.displayName} when you pick a model. Used for telemetry cost estimates."
        } else {
            "Enter USD per 1M tokens so chat telemetry can estimate spend for this preset."
        }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = helperText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (usesDiscoveryPricing) {
            DiscoveryPricingReadOnlyPanel(
                config = config,
                selectedModelMetadata = selectedModelMetadata,
            )
        } else {
            ManualPricingFields(
                config = config,
                onConfigChange = onConfigChange,
            )
        }
    }
}

@Composable
private fun DiscoveryPricingReadOnlyPanel(
    config: ApiModelConfigUi,
    selectedModelMetadata: DiscoveredApiModelUi?,
) {
    val prompt =
        ApiModelPricingFormat.parseUsdPerMillion(config.promptPriceUsdPerMillion)
            ?: selectedModelMetadata?.promptPrice
    val completion =
        ApiModelPricingFormat.parseUsdPerMillion(config.completionPriceUsdPerMillion)
            ?: selectedModelMetadata?.completionPrice
    val cached =
        ApiModelPricingFormat.parseUsdPerMillion(config.cachedPromptPriceUsdPerMillion)
            ?: selectedModelMetadata?.cachedPromptPrice

    EnclaveTelemetryMetricPanel {
        EnclaveTelemetryMetricRow(
            label = "Input (prompt)",
            value = ApiModelPricingFormat.displayUsdPerMillion(prompt),
        )
        EnclaveTelemetryMetricRow(
            label = "Output (completion)",
            value = ApiModelPricingFormat.displayUsdPerMillion(completion),
        )
        EnclaveTelemetryMetricRow(
            label = "Cached input",
            value = ApiModelPricingFormat.displayUsdPerMillion(cached),
        )
    }
}

@Composable
private fun ManualPricingFields(
    config: ApiModelConfigUi,
    onConfigChange: (ApiModelConfigUi) -> Unit,
) {
    EnclaveOutlinedTextField(
        value = config.promptPriceUsdPerMillion,
        onValueChange = { onConfigChange(config.copy(promptPriceUsdPerMillion = it)) },
        label = { Text("Input price (USD / 1M tokens)") },
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions =
            KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Next,
            ),
    )
    EnclaveOutlinedTextField(
        value = config.completionPriceUsdPerMillion,
        onValueChange = { onConfigChange(config.copy(completionPriceUsdPerMillion = it)) },
        label = { Text("Output price (USD / 1M tokens)") },
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions =
            KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Next,
            ),
    )
    EnclaveOutlinedTextField(
        value = config.cachedPromptPriceUsdPerMillion,
        onValueChange = { onConfigChange(config.copy(cachedPromptPriceUsdPerMillion = it)) },
        label = { Text("Cached input price (optional, USD / 1M)") },
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions =
            KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Done,
            ),
    )
}
