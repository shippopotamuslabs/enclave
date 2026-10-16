package com.browntowndev.enclave.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.browntowndev.enclave.core.ui.component.enclave.EnclavePrimaryButton
import com.browntowndev.enclave.core.ui.theme.EnclaveTheme
import com.browntowndev.enclave.core.ui.theme.EnclaveThemeId

/** Both-family screenshot goldens for a shared component (ENC-19). */
@PreviewTest
@Preview(name = "PrimaryButton Tonal Light", showBackground = true)
@Composable
fun EnclavePrimaryButtonTonalLightPreview() {
    EnclaveTheme(darkTheme = false, themeFamily = EnclaveThemeId.Tonal) {
        EnclavePrimaryButton(text = "Continue", onClick = {})
    }
}

@PreviewTest
@Preview(name = "PrimaryButton Tonal Dark", showBackground = true)
@Composable
fun EnclavePrimaryButtonTonalDarkPreview() {
    EnclaveTheme(darkTheme = true, themeFamily = EnclaveThemeId.Tonal) {
        EnclavePrimaryButton(text = "Continue", onClick = {})
    }
}

@PreviewTest
@Preview(name = "PrimaryButton Neo Light", showBackground = true)
@Composable
fun EnclavePrimaryButtonNeoLightPreview() {
    EnclaveTheme(darkTheme = false, themeFamily = EnclaveThemeId.NeoBrutalist) {
        EnclavePrimaryButton(text = "Continue", onClick = {})
    }
}

@PreviewTest
@Preview(name = "PrimaryButton Neo Dark", showBackground = true)
@Composable
fun EnclavePrimaryButtonNeoDarkPreview() {
    EnclaveTheme(darkTheme = true, themeFamily = EnclaveThemeId.NeoBrutalist) {
        EnclavePrimaryButton(text = "Continue", onClick = {})
    }
}
