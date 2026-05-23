package com.browntowndev.enclave.feature.chat.components

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.InsertPhoto
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import com.browntowndev.enclave.core.ui.component.StandardTrailingAction
import com.browntowndev.enclave.core.ui.component.neo.NeoActionRow
import com.browntowndev.enclave.core.ui.component.neo.NeoDisabledActionTooltip
import com.browntowndev.enclave.core.ui.component.neo.rememberNeoDisabledClick
import com.browntowndev.enclave.core.ui.component.neo.NeoIconButton
import com.browntowndev.enclave.core.ui.component.neo.NeoModalBottomSheet
import com.browntowndev.enclave.core.ui.component.neo.NeoSheetHeader
import com.browntowndev.enclave.core.ui.component.neo.neoBorderColor
import com.browntowndev.enclave.core.ui.component.neo.neoButtonShadowOffset
import com.browntowndev.enclave.core.ui.component.neo.neoShadowColor
import com.browntowndev.enclave.core.ui.component.neo.neoSurface
import com.browntowndev.enclave.core.ui.theme.EnclaveNeoTokens
import com.browntowndev.enclave.core.ui.component.UniversalImageAttachment
import com.browntowndev.enclave.core.ui.component.UniversalInputBar
import com.browntowndev.enclave.core.ui.component.UniversalPromptTextField
import com.browntowndev.enclave.core.ui.component.UniversalVoiceTrailingAction
import com.browntowndev.enclave.core.ui.theme.EnclaveTheme
import com.browntowndev.enclave.domain.port.media.SpeechState
import com.browntowndev.enclave.domain.port.media.isActiveVoiceInput
import com.browntowndev.enclave.feature.chat.ChatModeUi

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongParameterList")
@Composable
fun InputBar(
    inputText: String,
    speechState: SpeechState,
    selectedImageUris: List<String>,
    selectedFileName: String?,
    isPhotoAttachmentEnabled: Boolean,
    photoAttachmentMaxItems: Int,
    photoAttachmentDisabledReason: String?,
    selectedMode: ChatModeUi,
    isGenerating: Boolean,
    canStop: Boolean,
    isGlobalInferenceBlocked: Boolean = false,
    onInputChange: (String) -> Unit,
    onModeChange: (ChatModeUi) -> Unit,
    onSend: (String) -> Unit,
    onStopGenerating: () -> Unit,
    onImageAttach: () -> Unit,
    onFileAttach: () -> Unit,
    onClearImage: (String) -> Unit,
    onClearFile: () -> Unit,
    onMicClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var modeExpanded by remember { mutableStateOf(false) }
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

    if (showAttachmentSheet) {
        NeoModalBottomSheet(
            onDismissRequest = { showAttachmentSheet = false },
            sheetState = sheetState,
        ) {
            NeoSheetHeader(
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
        onExpandToggle = { isExpanded = !isExpanded },
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
                            .neoSurface(
                                backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                                borderColor = neoBorderColor(),
                                withShadow = false,
                                shape = EnclaveNeoTokens.shape,
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp),
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
                    NeoIconButton(
                        onClick = { showAttachmentSheet = true },
                        icon = Icons.Default.Add,
                        contentDescription = "Show attachment options",
                    )
                }
            },
        trailingAction = {
            if (!speechState.isActiveVoiceInput) {
                Box {
                    StandardTrailingAction(
                        painter = painterResource(selectedMode.iconRes),
                        onClick = { modeExpanded = true },
                        modifier = Modifier.focusProperties { canFocus = false },
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        description = selectedMode.getDisplayName(context),
                    )
                    DropdownMenu(
                        expanded = modeExpanded,
                        onDismissRequest = { modeExpanded = false },
                        properties = PopupProperties(focusable = false),
                        modifier =
                            Modifier
                                .width(200.dp)
                                .neoSurface(
                                    backgroundColor = MaterialTheme.colorScheme.surface,
                                    borderColor = neoBorderColor(),
                                    withShadow = true,
                                    shadowColor = neoShadowColor(),
                                    shadowOffset = neoButtonShadowOffset(),
                                    shape = EnclaveNeoTokens.shape,
                                ),
                    ) {
                        ChatModeUi.entries.forEach { mode ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        mode.getDisplayName(context),
                                        fontWeight = FontWeight.Bold,
                                    )
                                },
                                onClick = {
                                    onModeChange(mode)
                                    modeExpanded = false
                                },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(mode.iconRes),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
            }

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
    NeoDisabledActionTooltip(
        enabled = enabled,
        disabledReason = disabledReason,
    ) {
        val handleClick = rememberNeoDisabledClick(enabled = enabled, onEnabledClick = onClick)
        NeoActionRow(
            title = label,
            icon = icon,
            enabled = enabled,
            onClick = handleClick,
        )
    }
}

