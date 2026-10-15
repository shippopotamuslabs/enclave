package com.browntowndev.enclave.feature.chat.components

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.InsertPhoto
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.browntowndev.enclave.core.ui.component.UniversalImageAttachment
import com.browntowndev.enclave.core.ui.component.UniversalInputBar
import com.browntowndev.enclave.core.ui.component.UniversalPromptTextField
import com.browntowndev.enclave.core.ui.component.UniversalVoiceTrailingAction
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveActionRow
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveDisabledActionTooltip
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveIconButton
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveModalBottomSheet
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveSheetHeader
import com.browntowndev.enclave.core.ui.component.enclave.enclaveBorderColor
import com.browntowndev.enclave.core.ui.component.enclave.enclaveSurface
import com.browntowndev.enclave.core.ui.component.enclave.rememberNeoDisabledClick
import com.browntowndev.enclave.core.ui.theme.EnclaveNeoTokens
import com.browntowndev.enclave.domain.port.media.SpeechState
import com.browntowndev.enclave.domain.port.media.isActiveVoiceInput
import com.browntowndev.enclave.feature.chat.ChatModeUi

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongParameterList", "UnusedParameter")
@Composable
fun InputBar(
    inputText: String,
    speechState: SpeechState,
    selectedImageUris: List<String>,
    selectedFileName: String?,
    isPhotoAttachmentEnabled: Boolean,
    photoAttachmentMaxItems: Int,
    photoAttachmentDisabledReason: String?,
    selectedMode: ChatModeUi = ChatModeUi.FAST,
    isGenerating: Boolean,
    canStop: Boolean,
    isGlobalInferenceBlocked: Boolean = false,
    isLiveVoiceEnabled: Boolean = false,
    onInputChange: (String) -> Unit,
    onModeChange: (ChatModeUi) -> Unit = {},
    onSend: (String) -> Unit,
    onStopGenerating: () -> Unit,
    onImageAttach: () -> Unit,
    onFileAttach: () -> Unit,
    onClearImage: (String) -> Unit,
    onClearFile: () -> Unit,
    onMicClick: () -> Unit,
    onLiveVoiceClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var isExpanded by remember { mutableStateOf(false) }
    var showAttachmentSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { isGranted ->
            if (isGranted) {
                onMicClick()
            } else {
                Toast.makeText(context, "Microphone permission is required for speech-to-text", Toast.LENGTH_SHORT).show()
            }
        }

    val liveVoicePermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { isGranted ->
            if (isGranted) {
                onLiveVoiceClick()
            } else {
                Toast.makeText(context, "Microphone permission is required for Live Voice", Toast.LENGTH_SHORT).show()
            }
        }

    if (showAttachmentSheet) {
        EnclaveModalBottomSheet(
            onDismissRequest = { showAttachmentSheet = false },
            sheetState = sheetState,
        ) {
            EnclaveSheetHeader(
                title = "Attach",
                onDismiss = { showAttachmentSheet = false },
            )
            Spacer(modifier = Modifier.height(8.dp))
            AttachmentMenuItem(
                icon = Icons.Default.InsertPhoto,
                label = "Attach Photo",
                onClick = {
                    showAttachmentSheet = false
                    onImageAttach()
                },
                enabled =
                    isPhotoAttachmentEnabled &&
                        selectedImageUris.size < photoAttachmentMaxItems,
                disabledReason = photoAttachmentDisabledReason,
            )
            Spacer(modifier = Modifier.height(8.dp))
            AttachmentMenuItem(
                icon = Icons.Default.AttachFile,
                label = "Attach File",
                onClick = {
                    showAttachmentSheet = false
                    onFileAttach()
                },
            )
        }
    }

    UniversalInputBar(
        isExpanded = isExpanded,
        modifier =
            modifier.then(
                if (!isExpanded && !speechState.isActiveVoiceInput) {
                    Modifier.clickable { focusRequester.requestFocus() }
                } else {
                    Modifier
                },
            ),
        attachmentContent = {
            AnimatedVisibility(visible = selectedImageUris.isNotEmpty() && speechState == SpeechState.Idle) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    selectedImageUris.forEach { uri ->
                        UniversalImageAttachment(
                            uri = uri,
                            onClear = { onClearImage(uri) },
                        )
                    }
                }
            }

            AnimatedVisibility(visible = selectedFileName != null && speechState == SpeechState.Idle) {
                Row(
                    modifier =
                        Modifier
                            .padding(start = 16.dp, top = 8.dp, bottom = 8.dp)
                            .enclaveSurface(
                                backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                                borderColor = enclaveBorderColor(),
                                withShadow = false,
                                shape = EnclaveNeoTokens.shape,
                            ).padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.AttachFile,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = selectedFileName ?: "",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 200.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove file",
                        modifier =
                            Modifier
                                .size(16.dp)
                                .clickable { onClearFile() },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        inputContent = {
            UniversalPromptTextField(
                value = inputText,
                onValueChange = onInputChange,
                speechState = speechState,
                idlePlaceholder = "Enter the Enclave",
                isExpanded = isExpanded,
                focusRequester = focusRequester,
            )
        },
        actionContent =
            if (speechState.isActiveVoiceInput) {
                null
            } else {
                {
                    EnclaveIconButton(
                        onClick = { showAttachmentSheet = true },
                        icon = Icons.Default.Add,
                        contentDescription = "Show attachment options",
                    )
                }
            },
        trailingAction = {
            UniversalVoiceTrailingAction(
                inputText = inputText,
                speechState = speechState,
                isGenerating = isGenerating,
                canStop = canStop,
                onSend = onSend,
                onStop = onStopGenerating,
                onMicClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        onMicClick()
                    } else {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                isGlobalInferenceBlocked = isGlobalInferenceBlocked,
                onCollapse = { isExpanded = false },
                onLiveVoiceClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        onLiveVoiceClick()
                    } else {
                        liveVoicePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                isLiveVoiceEnabled = isLiveVoiceEnabled,
                isExpanded = isExpanded,
                onExpandToggle = { isExpanded = !isExpanded },
            )
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AttachmentMenuItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    disabledReason: String? = null,
) {
    EnclaveDisabledActionTooltip(
        enabled = enabled,
        disabledReason = disabledReason,
    ) {
        val handleClick = rememberNeoDisabledClick(enabled = enabled, onEnabledClick = onClick)
        EnclaveActionRow(
            title = label,
            icon = icon,
            enabled = enabled,
            onClick = handleClick,
        )
    }
}
