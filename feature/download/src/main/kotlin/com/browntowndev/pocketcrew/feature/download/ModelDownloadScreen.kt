package com.browntowndev.pocketcrew.feature.download

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SignalWifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.browntowndev.pocketcrew.domain.model.download.DownloadStatus
import com.browntowndev.pocketcrew.domain.model.download.FileStatus
import com.browntowndev.pocketcrew.domain.model.inference.ModelFile
import com.browntowndev.pocketcrew.domain.model.download.DownloadModelsResult
import com.browntowndev.pocketcrew.feature.download.DownloadViewModel.FileProgressUiModel
import com.browntowndev.pocketcrew.core.ui.theme.PocketCrewTheme
import com.browntowndev.pocketcrew.core.ui.util.FeatureFlags
import com.browntowndev.pocketcrew.core.data.util.formatBytes

/**
 * Full-screen model download status page.
 * Automatically shown when models need to be downloaded.
 * Handles POST_NOTIFICATIONS permission for Android 13+ to ensure
 * foreground service notifications appear during background downloads.
 */
@Composable
fun ModelDownloadScreen(
    modelsResult: DownloadModelsResult?,
    errorMessage: String? = null,
    onReady: () -> Unit = {},
    viewModel: DownloadViewModel = hiltViewModel<DownloadViewModel, DownloadViewModel.Factory>(
        key = (modelsResult?.modelsToDownload ?: emptyList()).hashCode().toString(),
        creationCallback = { factory ->
            // Pass autoStartDownloads = false to prevent auto-start in init
            // This ensures downloads are gated on notification permission confirmation
            factory.create(modelsResult, errorMessage, autoStartDownloads = false)
        }
    )
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val downloadState by viewModel.downloadState.collectAsStateWithLifecycle()
    val wifiOnly by viewModel.wifiOnly.collectAsStateWithLifecycle()
    val showWifiDialog by viewModel.showWifiDialog.collectAsStateWithLifecycle()
    val fileProgressList by viewModel.fileProgressList.collectAsStateWithLifecycle()
    
    // Track lifecycle for foreground state to prevent race condition with WorkManager foreground service
    LaunchedEffect(lifecycleOwner) {
        // Check current state immediately to handle case where activity is already in ON_RESUME
        // when this LaunchedEffect runs (observer would miss the event if we only listen for future events)
        if (lifecycleOwner.lifecycle.currentState == Lifecycle.State.RESUMED) {
            viewModel.onAppForegrounded()
        }
        
        lifecycleOwner.lifecycle.addObserver(LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.onAppForegrounded()
                Lifecycle.Event.ON_PAUSE -> viewModel.onAppBackgrounded()
                else -> {}
            }
        })
    }

    // Permission state for POST_NOTIFICATIONS (Android 13+)
    var hasNotificationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    // Track if we should show rationale (user denied once, but can be shown again)
    var showRationale by remember { mutableStateOf(false) }

    // Permission launcher using native AndroidX API
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
        if (isGranted) {
            // Permission granted - trigger model check and download
            viewModel.checkModels()
        } else {
            // Permission denied - check if we should show rationale
            val activity = context.findActivity()
            if (activity != null) {
                showRationale = activity.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Request permission on first screen appearance (Android 13+)
    LaunchedEffect(Unit) {
        if (!hasNotificationPermission) {
            val activity = context.findActivity()
            if (activity != null) {
                // Check if we should show rationale first
                if (activity.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                    showRationale = true
                } else {
                    // First time request - launch directly
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            // Permission already granted or not needed - only check models if not already downloading
            // This prevents destructive UI re-initialization that hides files
            val currentStatus = downloadState.status
            if (currentStatus == DownloadStatus.IDLE || currentStatus == DownloadStatus.ERROR) {
                viewModel.checkModels()
            }
        }
    }

    // Navigate to main UI when ready
    LaunchedEffect(downloadState.status) {
        if (downloadState.status == DownloadStatus.READY) {
            onReady()
        }
    }

    // Permission rationale dialog
    if (showRationale) {
        NotificationPermissionRationaleDialog(
            onRequestPermission = {
                showRationale = false
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
            onDismiss = {
                showRationale = false
                // User dismissed - still try to download but notifications won't show
                viewModel.checkModels()
            }
        )
    }

    // WiFi-only blocked dialog
    if (showWifiDialog) {
        WifiBlockedDialog(
            onDownloadOnMobile = { viewModel.downloadOnMobileData() },
            onDismiss = { viewModel.dismissWifiDialog() }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            DownloadControls(
                status = downloadState.status,
                wifiOnly = wifiOnly,
                onPause = { viewModel.pauseDownloads() },
                onResume = { viewModel.resumeDownloads() },
                onCancel = { viewModel.cancelDownloads() },
                onRetry = { viewModel.retryFailed() },
                onWifiOnlyChange = { viewModel.setWifiOnly(it) }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            // Header
            DownloadHeader(
                status = downloadState.status ?: DownloadStatus.IDLE,
                progress = { downloadState.overallProgress ?: 0f },
                modelsComplete = downloadState.modelsComplete ?: 0,
                modelsTotal = downloadState.modelsTotal ?: 0,
                speedMBs = downloadState.currentSpeedMBs,
                eta = downloadState.estimatedTimeRemaining
            )

            Spacer(modifier = Modifier.height(24.dp))

            // File list
            FileProgressList(
                files = fileProgressList,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Error message
            downloadState.errorMessage?.let { error ->
                ErrorBanner(
                    message = error,
                    onRetry = { viewModel.retryFailed() }
                )
            }

            // Background download notice
            BackgroundNotice(hasPermission = hasNotificationPermission)
        }
    }
}

/**
 * Extension to find the current Activity from a Context.
 * Required for shouldShowRequestPermissionRationale API.
 */
private fun Context.findActivity(): Activity? {
    var context = this
    while (context is android.content.ContextWrapper) {
        if (context is Activity) {
            return context
        }
        context = context.baseContext
    }
    return null
}

@Composable
private fun DownloadHeader(
    status: DownloadStatus,
    progress: () -> Float,
    modelsComplete: Int,
    modelsTotal: Int,
    speedMBs: Double?,
    eta: String?
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Icon
        Icon(
            imageVector = Icons.Default.CloudDownload,
            contentDescription = "Download status",
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Title
        Text(
            text = when (status) {
                DownloadStatus.CHECKING -> "Checking on the crew..."
                DownloadStatus.DOWNLOADING -> "Downloading Crew"
                DownloadStatus.PAUSED -> "Downloads Paused"
                DownloadStatus.ERROR -> "Download Error"
                else -> "Crew"
            },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Progress
        if (status == DownloadStatus.CHECKING) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(50)),
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        } else {
            val animatedProgress by animateFloatAsState(
                targetValue = progress(),
                label = "progress"
            )

            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(50)),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Butt
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Stats row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "$modelsComplete of $modelsTotal models",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "${(progress() * 100).toInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Speed and ETA
        if (status == DownloadStatus.DOWNLOADING && (speedMBs != null || eta != null)) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                speedMBs?.let {
                    Text(
                        text = String.format("%.1f MB/s", it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (speedMBs != null && eta != null) {
                    Text(
                        text = " • ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                eta?.let {
                    Text(
                        text = "ETA: $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Stable
@Composable
private fun FileProgressList(
    files: List<FileProgressUiModel>,
    modifier: Modifier = Modifier
) {
    if (files.isEmpty()) {
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Waiting for model configuration...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    } else {
        LazyColumn(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                items = files,
                key = { it.displayName }
            ) { file ->
                FileProgressItem(
                    file = file,
                    progress = { file.progress }
                )
            }
        }
    }
}

@Composable
private fun FileProgressItem(
    file: FileProgressUiModel,
    progress: () -> Float
) {
    val statusColor by animateColorAsState(
        targetValue = when (file.status) {
            FileStatus.COMPLETE -> MaterialTheme.colorScheme.primary
            FileStatus.FAILED -> MaterialTheme.colorScheme.error
            FileStatus.DOWNLOADING -> MaterialTheme.colorScheme.primary
            FileStatus.PAUSED -> MaterialTheme.colorScheme.surfaceVariant
            FileStatus.QUEUED -> MaterialTheme.colorScheme.outline
        },
        label = "statusColor"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status icon
                Icon(
                    imageVector = when (file.status) {
                        FileStatus.COMPLETE -> Icons.Default.CheckCircle
                        FileStatus.FAILED -> Icons.Default.Error
                        FileStatus.DOWNLOADING -> Icons.Default.CloudDownload
                        FileStatus.PAUSED -> Icons.Default.Pause
                        FileStatus.QUEUED -> Icons.Default.CloudDownload
                    },
                    contentDescription = when (file.status) {
                        FileStatus.COMPLETE -> "Download complete"
                        FileStatus.FAILED -> "Download failed"
                        FileStatus.DOWNLOADING -> "Currently downloading"
                        FileStatus.PAUSED -> "Download paused"
                        FileStatus.QUEUED -> "Queued for download"
                    },
                    tint = statusColor,
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Display name with anthropomorphized model roles
                Text(
                    text = file.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                // Status text
                Text(
                    text = when (file.status) {
                        FileStatus.COMPLETE -> "Complete"
                        FileStatus.FAILED -> "Failed"
                        FileStatus.DOWNLOADING -> file.speedMBs?.let {
                            String.format("%.1f MB/s", it)
                        } ?: "Downloading..."
                        FileStatus.PAUSED -> "Paused"
                        FileStatus.QUEUED -> "Waiting..."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColor
                )
            }

            // Progress bar
            if (file.status == FileStatus.DOWNLOADING || file.status == FileStatus.PAUSED) {
                Spacer(modifier = Modifier.height(8.dp))

                val animatedProgress by animateFloatAsState(
                    targetValue = progress(),
                    label = "fileProgress"
                )

                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(50)),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeCap = StrokeCap.Butt
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Size text
                Text(
                    text = "${formatBytes(file.bytesDownloaded)} / ${formatBytes(file.totalBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DownloadControls(
    status: DownloadStatus,
    wifiOnly: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onWifiOnlyChange: (Boolean) -> Unit
) {
    val allowCancel = FeatureFlags.ALLOW_CANCEL_DOWNLOAD

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // WiFi-only toggle (Always visible) - Full row is clickable with 48dp min touch target
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = wifiOnly,
                    onValueChange = onWifiOnlyChange,
                    role = Role.Switch
                )
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Download on Wi-Fi only",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = wifiOnly,
                onCheckedChange = null // Handled by parent toggleable
            )
        }

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when (status) {
                DownloadStatus.DOWNLOADING -> {
                    if (allowCancel) {
                        OutlinedButton(
                            onClick = onCancel,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.Cancel,
                                contentDescription = "Cancel download"
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Cancel")
                        }
                    }
                    Button(
                        onClick = onPause,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.Pause,
                            contentDescription = "Pause download"
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Pause")
                    }
                }
                DownloadStatus.PAUSED -> {
                    if (allowCancel) {
                        OutlinedButton(
                            onClick = onCancel,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.Cancel,
                                contentDescription = "Cancel download"
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Cancel")
                        }
                    }
                    Button(
                        onClick = onResume,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Resume download"
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Resume")
                    }
                }
                DownloadStatus.ERROR -> {
                    Button(
                        onClick = onRetry,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Retry all downloads"
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Retry All")
                    }
                }
                else -> {
                    if (status != DownloadStatus.READY && allowCancel) {
                        OutlinedButton(
                            onClick = onCancel,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Default.Cancel,
                                contentDescription = "Cancel download"
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Cancel")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorBanner(
    message: String,
    onRetry: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = "Error",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}

@Composable
private fun BackgroundNotice(hasPermission: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.CloudDownload,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = if (hasPermission) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.error
            }
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (hasPermission) {
                "Downloads continue in background via notification"
            } else {
                "Enable notifications for background download progress"
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (hasPermission) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.error
            }
        )
    }
}

@Composable
private fun WifiBlockedDialog(
    onDownloadOnMobile: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.SignalWifiOff,
                contentDescription = "Wi-Fi disabled",
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text("Wi-Fi Only Enabled")
        },
        text = {
            Text("Downloads are paused because you're not connected to Wi-Fi. Would you like to download using mobile data instead?")
        },
        confirmButton = {
            Button(onClick = onDownloadOnMobile) {
                Text("Download on Mobile Data")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Dialog shown when user has previously denied notification permission.
 * Explains why the permission is needed for background download progress.
 */
@Composable
private fun NotificationPermissionRationaleDialog(
    onRequestPermission: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.CloudDownload,
                contentDescription = "Notifications",
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text("Enable Download Notifications")
        },
        text = {
            Text("Pocket Crew needs notification permission to show download progress when the app is in the background. This allows you to monitor AI model downloads even when you switch to other apps.\n\nYour notification will show real-time progress, speed, and ETA for the download.")
        },
        confirmButton = {
            Button(onClick = onRequestPermission) {
                Text("Enable Notifications")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Not Now")
            }
        }
    )
}

// ==================== PREVIEWS ====================

private val fakeFilesAllDownloading = listOf(
    FileProgressUiModel(
        filename = "model-qwen3-8b.gguf",
        displayName = "Qwen 3 8B",
        bytesDownloaded = 2_500_000_000L,
        totalBytes = 5_000_000_000L,
        status = FileStatus.DOWNLOADING,
        speedMBs = 12.4
    ),
    FileProgressUiModel(
        filename = "model-embeddings.gguf",
        displayName = "Embeddings",
        bytesDownloaded = 800_000_000L,
        totalBytes = 2_000_000_000L,
        status = FileStatus.DOWNLOADING,
        speedMBs = 8.2
    ),
    FileProgressUiModel(
        filename = "model-tokenizer.gguf",
        displayName = "Tokenizer",
        bytesDownloaded = 0L,
        totalBytes = 500_000_000L,
        status = FileStatus.QUEUED,
        speedMBs = null
    )
)

private val fakeFilesMixedStates = listOf(
    FileProgressUiModel(
        filename = "model-qwen3-8b.gguf",
        displayName = "Qwen 3 8B",
        bytesDownloaded = 5_000_000_000L,
        totalBytes = 5_000_000_000L,
        status = FileStatus.COMPLETE,
        speedMBs = null
    ),
    FileProgressUiModel(
        filename = "model-embeddings.gguf",
        displayName = "Embeddings",
        bytesDownloaded = 1_500_000_000L,
        totalBytes = 2_000_000_000L,
        status = FileStatus.DOWNLOADING,
        speedMBs = 15.7
    ),
    FileProgressUiModel(
        filename = "model-tokenizer.gguf",
        displayName = "Tokenizer",
        bytesDownloaded = 0L,
        totalBytes = 500_000_000L,
        status = FileStatus.FAILED,
        speedMBs = null
    ),
    FileProgressUiModel(
        filename = "model-vision.gguf",
        displayName = "Vision Encoder",
        bytesDownloaded = 400_000_000L,
        totalBytes = 1_200_000_000L,
        status = FileStatus.PAUSED,
        speedMBs = null
    )
)

private val fakeFilesAllComplete = listOf(
    FileProgressUiModel(
        filename = "model-qwen3-8b.gguf",
        displayName = "Qwen 3 8B",
        bytesDownloaded = 5_000_000_000L,
        totalBytes = 5_000_000_000L,
        status = FileStatus.COMPLETE,
        speedMBs = null
    ),
    FileProgressUiModel(
        filename = "model-embeddings.gguf",
        displayName = "Embeddings",
        bytesDownloaded = 2_000_000_000L,
        totalBytes = 2_000_000_000L,
        status = FileStatus.COMPLETE,
        speedMBs = null
    ),
    FileProgressUiModel(
        filename = "model-tokenizer.gguf",
        displayName = "Tokenizer",
        bytesDownloaded = 500_000_000L,
        totalBytes = 500_000_000L,
        status = FileStatus.COMPLETE,
        speedMBs = null
    )
)

@Preview(name = "Downloading - Multiple Files")
@Composable
private fun PreviewDownloadHeaderDownloading() {
    PocketCrewTheme {
        Scaffold { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            ) {
                DownloadHeader(
                    status = DownloadStatus.DOWNLOADING,
                    progress = { 0.65f },
                    modelsComplete = 1,
                    modelsTotal = 3,
                    speedMBs = 12.4,
                    eta = "5 min"
                )
                Spacer(modifier = Modifier.height(24.dp))
                FileProgressList(
                    files = fakeFilesAllDownloading,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Preview(name = "Mixed States")
@Composable
private fun PreviewDownloadMixedStates() {
    PocketCrewTheme {
        Scaffold { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            ) {
                DownloadHeader(
                    status = DownloadStatus.DOWNLOADING,
                    progress = { 0.72f },
                    modelsComplete = 1,
                    modelsTotal = 4,
                    speedMBs = 15.7,
                    eta = "3 min"
                )
                Spacer(modifier = Modifier.height(24.dp))
                FileProgressList(
                    files = fakeFilesMixedStates,
                    modifier = Modifier.weight(1f)
                )
                ErrorBanner(
                    message = "Failed to download Tokenizer: Network timeout",
                    onRetry = {}
                )
            }
        }
    }
}

@Preview(name = "Checking Status")
@Composable
private fun PreviewDownloadChecking() {
    PocketCrewTheme {
        Scaffold { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            ) {
                DownloadHeader(
                    status = DownloadStatus.CHECKING,
                    progress = { 0f },
                    modelsComplete = 0,
                    modelsTotal = 3,
                    speedMBs = null,
                    eta = null
                )
            }
        }
    }
}

@Preview(name = "Paused")
@Composable
private fun PreviewDownloadPaused() {
    PocketCrewTheme {
        Scaffold { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            ) {
                DownloadHeader(
                    status = DownloadStatus.PAUSED,
                    progress = { 0.42f },
                    modelsComplete = 1,
                    modelsTotal = 3,
                    speedMBs = null,
                    eta = null
                )
                Spacer(modifier = Modifier.height(24.dp))
                FileProgressList(
                    files = fakeFilesMixedStates,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Preview(name = "Error State")
@Composable
private fun PreviewDownloadError() {
    PocketCrewTheme {
        Scaffold { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            ) {
                DownloadHeader(
                    status = DownloadStatus.ERROR,
                    progress = { 0.35f },
                    modelsComplete = 0,
                    modelsTotal = 3,
                    speedMBs = null,
                    eta = null
                )
                Spacer(modifier = Modifier.height(24.dp))
                FileProgressList(
                    files = fakeFilesMixedStates,
                    modifier = Modifier.weight(1f)
                )
                ErrorBanner(
                    message = "Connection lost: Please check your internet connection",
                    onRetry = {}
                )
            }
        }
    }
}

@Preview(name = "Complete")
@Composable
private fun PreviewDownloadComplete() {
    PocketCrewTheme {
        Scaffold { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            ) {
                DownloadHeader(
                    status = DownloadStatus.READY,
                    progress = { 1f },
                    modelsComplete = 3,
                    modelsTotal = 3,
                    speedMBs = null,
                    eta = null
                )
                Spacer(modifier = Modifier.height(24.dp))
                FileProgressList(
                    files = fakeFilesAllComplete,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Preview(name = "File States")
@Composable
private fun PreviewFileProgressItems() {
    PocketCrewTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Downloading", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            FileProgressItem(
                file = FileProgressUiModel("model.gguf", "Qwen 3 8B", 2_500_000_000L, 5_000_000_000L, FileStatus.DOWNLOADING, 12.4),
                progress = { 0.5f }
            )

            Text("Complete", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            FileProgressItem(
                file = FileProgressUiModel("model.gguf", "Embeddings", 2_000_000_000L, 2_000_000_000L, FileStatus.COMPLETE, null),
                progress = { 1f }
            )

            Text("Failed", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            FileProgressItem(
                file = FileProgressUiModel("model.gguf", "Tokenizer", 150_000_000L, 500_000_000L, FileStatus.FAILED, null),
                progress = { 0.3f }
            )

            Text("Paused", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            FileProgressItem(
                file = FileProgressUiModel("model.gguf", "Vision Encoder", 400_000_000L, 1_200_000_000L, FileStatus.PAUSED, null),
                progress = { 0.33f }
            )

            Text("Queued", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            FileProgressItem(
                file = FileProgressUiModel("model.gguf", "Large Model", 0L, 8_000_000_000L, FileStatus.QUEUED, null),
                progress = { 0f }
            )
        }
    }
}

@Preview(name = "Long ETA")
@Composable
private fun PreviewDownloadLongETA() {
    PocketCrewTheme {
        Scaffold { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            ) {
                DownloadHeader(
                    status = DownloadStatus.DOWNLOADING,
                    progress = { 0.15f },
                    modelsComplete = 0,
                    modelsTotal = 5,
                    speedMBs = 2.3,
                    eta = "1.8 hours"
                )
            }
        }
    }
}

@Preview(name = "Background Notice")
@Composable
private fun PreviewBackgroundNotice() {
    PocketCrewTheme {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("With Permission", style = MaterialTheme.typography.titleMedium)
            BackgroundNotice(hasPermission = true)
            androidx.compose.material3.HorizontalDivider()
            Text("Without Permission", style = MaterialTheme.typography.titleMedium)
            BackgroundNotice(hasPermission = false)
        }
    }
}

@Preview(name = "WiFi Blocked Dialog")
@Composable
private fun PreviewWifiBlockedDialog() {
    PocketCrewTheme {
        WifiBlockedDialog(onDownloadOnMobile = {}, onDismiss = {})
    }
}

@Preview(name = "Notification Permission Dialog")
@Composable
private fun PreviewNotificationPermissionDialog() {
    PocketCrewTheme {
        NotificationPermissionRationaleDialog(onRequestPermission = {}, onDismiss = {})
    }
}
