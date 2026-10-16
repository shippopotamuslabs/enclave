package com.browntowndev.enclave.feature.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveOutlinedTextField
import com.browntowndev.enclave.domain.model.inference.DeepSeekEndpoint

/**
 * DeepSeek BYOK base URL display. Official endpoint only; legacy URLs migrate on load.
 */
@Composable
fun DeepSeekBaseUrlField(
    asset: ApiModelAssetUi,
    onAssetChange: (ApiModelAssetUi) -> Unit,
) {
    LaunchedEffect(asset.baseUrl) {
        if (DeepSeekEndpoint.storedUrlNeedsMigration(asset.baseUrl)) {
            onAssetChange(asset.copy(baseUrl = DeepSeekEndpoint.OFFICIAL.baseUrl))
        }
    }

    EnclaveOutlinedTextField(
        value = DeepSeekEndpoint.OFFICIAL.baseUrl,
        onValueChange = {},
        readOnly = true,
        label = { Text("Base URL") },
        modifier = Modifier.fillMaxWidth(),
    )
}
