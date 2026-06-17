package com.browntowndev.enclave.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.browntowndev.enclave.core.ui.component.neo.NeoSegmentedControl
import com.browntowndev.enclave.domain.model.inference.ZaiEndpoint

/**
 * Z.ai BYOK endpoint picker. Only the two official OpenAI-compatible presets are supported;
 * there is no custom base-URL override. Legacy `api/anthropic` URLs migrate to [ZaiEndpoint.PUBLIC].
 */
@Composable
fun ZaiEndpointSelectionField(
    asset: ApiModelAssetUi,
    onAssetChange: (ApiModelAssetUi) -> Unit,
) {
    val endpoint = ZaiEndpoint.resolveEndpoint(asset.baseUrl)

    LaunchedEffect(asset.baseUrl) {
        if (ZaiEndpoint.storedUrlNeedsMigration(asset.baseUrl)) {
            onAssetChange(asset.copy(baseUrl = endpoint.baseUrl))
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Endpoint",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        NeoSegmentedControl(
            options = ZaiEndpoint.entries.map { it.displayName },
            selectedIndex = endpoint.ordinal,
            onOptionSelected = { index ->
                ZaiEndpoint.entries.getOrNull(index)?.let { selected ->
                    onAssetChange(asset.copy(baseUrl = selected.baseUrl))
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
