package com.browntowndev.enclave.feature.chat.components.detailbottomsheet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalInspectionMode

/**
 * Hosts [DetailBottomSheet] at screen scope (sibling to main content), matching
 * [com.browntowndev.enclave.feature.settings.TosBottomSheet] placement in Settings.
 *
 * Callers inside scrollable lists must not compose [DetailBottomSheet] directly;
 * use [LocalDetailBottomSheetController] instead.
 */
@Composable
fun ProvideDetailBottomSheet(content: @Composable () -> Unit) {
    var activeSheet by remember { mutableStateOf<HostedDetailSheet?>(null) }

    val controller =
        remember {
            DetailBottomSheetController(
                onShow = { config, elapsedSeconds ->
                    val userDismiss = config.onDismiss
                    activeSheet =
                        HostedDetailSheet(
                            config = config.withDismiss { userDismiss(); activeSheet = null },
                            elapsedSeconds = elapsedSeconds,
                        )
                },
                onUpdateElapsed = { elapsedSeconds ->
                    activeSheet = activeSheet?.copy(elapsedSeconds = elapsedSeconds)
                },
                onDismiss = {
                    activeSheet?.config?.onDismiss?.invoke()
                    activeSheet = null
                },
            )
        }

    CompositionLocalProvider(
        LocalDetailBottomSheetHostActive provides true,
        LocalDetailBottomSheetController provides controller,
    ) {
        content()
    }

    activeSheet?.let { hosted ->
        DetailBottomSheet(
            config = hosted.config,
            elapsedSeconds = hosted.elapsedSeconds,
        )
    }
}

private data class HostedDetailSheet(
    val config: DetailBottomSheetConfig,
    val elapsedSeconds: Int,
)

class DetailBottomSheetController internal constructor(
    private val onShow: (DetailBottomSheetConfig, Int) -> Unit,
    private val onUpdateElapsed: (Int) -> Unit,
    private val onDismiss: () -> Unit,
) {
    fun show(
        config: DetailBottomSheetConfig,
        elapsedSeconds: Int = 0,
    ) {
        onShow(config, elapsedSeconds)
    }

    fun updateElapsed(elapsedSeconds: Int) {
        onUpdateElapsed(elapsedSeconds)
    }

    fun dismiss() {
        onDismiss()
    }
}

/** True while [ProvideDetailBottomSheet] wraps the current subtree (e.g. [ChatScreen]). */
internal val LocalDetailBottomSheetHostActive = compositionLocalOf { false }

/**
 * Defaults to a no-op controller so @Preview roots (e.g. [MessageListPreviews]) do not crash.
 * [ProvideDetailBottomSheet] on [com.browntowndev.enclave.feature.chat.ChatScreen] supplies the real host.
 */
val LocalDetailBottomSheetController =
    compositionLocalOf { previewDetailBottomSheetController() }

/**
 * Resolves the detail sheet controller for list rows and assistant message chrome.
 */
@Composable
internal fun rememberDetailBottomSheetController(usePreviewStub: Boolean = LocalInspectionMode.current): DetailBottomSheetController {
    if (usePreviewStub || !LocalDetailBottomSheetHostActive.current) {
        return remember { previewDetailBottomSheetController() }
    }
    return LocalDetailBottomSheetController.current
}

private fun previewDetailBottomSheetController(): DetailBottomSheetController =
    DetailBottomSheetController(
        onShow = { _, _ -> },
        onUpdateElapsed = {},
        onDismiss = {},
    )

private fun DetailBottomSheetConfig.withDismiss(onDismiss: () -> Unit): DetailBottomSheetConfig =
    when (this) {
        is DetailBottomSheetConfig.Thinking -> copy(onDismiss = onDismiss)
        is DetailBottomSheetConfig.StepCompletion -> copy(onDismiss = onDismiss)
        is DetailBottomSheetConfig.Sources -> copy(onDismiss = onDismiss)
    }
