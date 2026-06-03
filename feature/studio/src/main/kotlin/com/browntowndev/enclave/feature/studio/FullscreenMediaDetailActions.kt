package com.browntowndev.enclave.feature.studio

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import com.browntowndev.enclave.core.ui.component.FullscreenMediaAction
import com.browntowndev.enclave.core.ui.component.FullscreenMediaActionPlacement

internal fun fullscreenSaveAction(onSave: () -> Unit): FullscreenMediaAction =
    FullscreenMediaAction(
        icon = Icons.Default.Download,
        label = "Save",
        onClick = onSave,
        placement = FullscreenMediaActionPlacement.PrimaryBar,
    )
