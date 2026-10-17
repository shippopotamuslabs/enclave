package com.browntowndev.enclave.core.ui.component.enclave

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.Preview
import com.browntowndev.enclave.core.ui.theme.EnclaveTheme
import com.browntowndev.enclave.core.ui.theme.EnclaveThemeAccessor
import com.browntowndev.enclave.core.ui.theme.EnclaveThemeId

/**
 * Spec-shaped bordered surface (replaces Material [androidx.compose.material3.Surface]).
 * Background, border width, and corner geometry come from the active theme spec via
 * [Modifier.enclaveSurface]; [EnclavePanel] is the column-oriented grouped variant.
 */
@Composable
fun EnclaveSurface(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
    shape: Shape = EnclaveThemeAccessor.shapes.panel,
    borderColor: Color = enclaveBorderColor(),
    withShadow: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier =
            modifier.enclaveSurface(
                backgroundColor = color,
                borderColor = borderColor,
                withShadow = withShadow,
                shape = shape,
            ),
        content = content,
    )
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true)
@Composable
private fun EnclaveSurfaceTonalLightPreview() {
    EnclaveTheme(darkTheme = false, themeFamily = EnclaveThemeId.Tonal) {
        EnclaveSurface { }
    }
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true)
@Composable
private fun EnclaveSurfaceTonalDarkPreview() {
    EnclaveTheme(darkTheme = true, themeFamily = EnclaveThemeId.Tonal) {
        EnclaveSurface { }
    }
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true)
@Composable
private fun EnclaveSurfaceNeoLightPreview() {
    EnclaveTheme(darkTheme = false, themeFamily = EnclaveThemeId.NeoBrutalist) {
        EnclaveSurface { }
    }
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true)
@Composable
private fun EnclaveSurfaceNeoDarkPreview() {
    EnclaveTheme(darkTheme = true, themeFamily = EnclaveThemeId.NeoBrutalist) {
        EnclaveSurface { }
    }
}
