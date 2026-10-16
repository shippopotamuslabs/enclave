package com.browntowndev.enclave.core.ui.component.enclave

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.browntowndev.enclave.core.ui.component.sheet.JumpFreeModalBottomSheet
import com.browntowndev.enclave.core.ui.theme.EnclaveNeoTokens

/**
 * Settings-style modal bottom sheet with neo-brutalist shape and horizontal padding.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnclaveModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    tonalElevation: Dp = Dp.Hairline,
    dragHandle: @Composable (() -> Unit)? = { BottomSheetDefaults.DragHandle() },
    content: @Composable ColumnScope.() -> Unit,
) {
    JumpFreeModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        shape = EnclaveNeoTokens.bottomSheetShape,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = tonalElevation,
        dragHandle = dragHandle,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = EnclaveNeoTokens.screenHorizontalPadding)
                    .padding(bottom = 24.dp),
            content = content,
        )
    }
}
