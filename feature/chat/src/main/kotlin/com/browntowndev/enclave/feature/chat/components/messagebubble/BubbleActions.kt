package com.browntowndev.enclave.feature.chat.components.messagebubble

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveSecondaryIconButton
import com.browntowndev.enclave.feature.chat.R

@Composable
internal fun BubbleActions(
    visible: Boolean,
    onEditClick: () -> Unit,
    onCopyClick: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.zIndex(-1f),
        enter = slideInVertically(initialOffsetY = { -it }) + expandVertically(expandFrom = Alignment.Top) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
    ) {
        Row(
            modifier = Modifier.padding(top = 4.dp, end = 12.dp),
        ) {
            EnclaveSecondaryIconButton(
                onClick = onEditClick,
                icon = Icons.Default.Edit,
                contentDescription = "Edit Message",
                size = 32.dp,
                iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            EnclaveSecondaryIconButton(
                onClick = onCopyClick,
                icon = painterResource(R.drawable.content_copy),
                contentDescription = "Copy message",
                size = 32.dp,
                iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
