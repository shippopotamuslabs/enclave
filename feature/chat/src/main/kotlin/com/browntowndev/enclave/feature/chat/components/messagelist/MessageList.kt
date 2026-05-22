package com.browntowndev.enclave.feature.chat.components.messagelist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.feature.chat.ArtifactCardUiModel
import com.browntowndev.enclave.feature.chat.ChatMessage
import com.browntowndev.enclave.feature.chat.ChatModeUi
import com.browntowndev.enclave.feature.chat.MessageRole
import com.browntowndev.enclave.feature.chat.ToolCallBannerUi
import kotlinx.coroutines.launch

// ~2–3 message rows ahead/behind at typical density; avoids retaining ~30 screens off-screen.
private val MessageListLazyCacheWindow = 360.dp

/** Test-visible alias for [MessageListLazyCacheWindow]. */
internal val MessageListLazyCacheWindowDp = MessageListLazyCacheWindow

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageList(
    modifier: Modifier = Modifier,
    messages: List<ChatMessage>,
    hasActiveIndicator: Boolean,
    activeToolCallBanner: ToolCallBannerUi? = null,
    activeIndicatorMessageId: MessageId? = null,
    hasTtsProviderAssigned: Boolean = false,
    isPreview: Boolean = false,
    chatMode: ChatModeUi = ChatModeUi.FAST,
    onEditMessage: (String) -> Unit = { _: String -> },
    onPlayAudio: (String) -> Unit = {},
    onArtifactCardClick: (MessageId, ArtifactCardUiModel) -> Unit = { _, _ -> },
    onNavigateToStudio: (
        ids: List<String>,
        uris: List<String>,
        types: List<String>,
        prompts: List<String>,
    ) -> Unit = { _, _, _, _ -> },
    onShareClick: (MessageId) -> Unit = {},
) {
    val onEditMessageRef = rememberUpdatedState(onEditMessage)
    val onPlayAudioRef = rememberUpdatedState(onPlayAudio)
    val onArtifactCardClickRef = rememberUpdatedState(onArtifactCardClick)
    val onNavigateToStudioRef = rememberUpdatedState(onNavigateToStudio)
    val onShareClickRef = rememberUpdatedState(onShareClick)
    val callbacks =
        remember {
            MessageListCallbacks(
                onEditMessage = { onEditMessageRef.value(it) },
                onPlayAudio = { onPlayAudioRef.value(it) },
                onArtifactCardClick = { messageId, card -> onArtifactCardClickRef.value(messageId, card) },
                onNavigateToStudio = { ids, uris, types, prompts ->
                    onNavigateToStudioRef.value(ids, uris, types, prompts)
                },
                onShareClick = { onShareClickRef.value(it) },
            )
        }
    val listState =
        rememberLazyListState(
            cacheWindow =
                remember {
                    LazyLayoutCacheWindow(
                        ahead = MessageListLazyCacheWindow,
                        behind = MessageListLazyCacheWindow,
                    )
                },
        )

    val latestUserMessageIndex = messages.indexOfLast { it.role == MessageRole.User }
    val latestUserMessageId = messages.getOrNull(latestUserMessageIndex)?.id
    val chatId = messages.firstOrNull()?.chatId

    MessageListScrollToLatestEffect(
        chatId = chatId,
        messagesNotEmpty = messages.isNotEmpty(),
        latestUserMessageId = latestUserMessageId,
        latestUserMessageIndex = latestUserMessageIndex,
        messageCount = messages.size,
        listState = listState,
    )

    if (messages.isEmpty() && !hasActiveIndicator) {
        MessageListEmptyState(modifier = modifier)
    } else {
        MessageListContent(
            modifier = modifier,
            messages = messages,
            chatId = chatId,
            latestUserMessageIndex = latestUserMessageIndex,
            listState = listState,
            activeIndicatorMessageId = activeIndicatorMessageId,
            activeToolCallBanner = activeToolCallBanner,
            hasTtsProviderAssigned = hasTtsProviderAssigned,
            chatMode = chatMode,
            callbacks = callbacks,
            isPreview = isPreview,
        )
    }
}

@Composable
private fun MessageListContent(
    modifier: Modifier,
    messages: List<ChatMessage>,
    chatId: ChatId?,
    latestUserMessageIndex: Int,
    listState: LazyListState,
    activeIndicatorMessageId: MessageId?,
    activeToolCallBanner: ToolCallBannerUi?,
    hasTtsProviderAssigned: Boolean,
    chatMode: ChatModeUi,
    callbacks: MessageListCallbacks,
    isPreview: Boolean,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val bottomContentPadding = 16.dp
        val itemSpacing = 12.dp
        val activeInteractionMinHeight =
            activeInteractionMinHeight(
                viewportHeight = maxHeight,
                bottomContentPadding = bottomContentPadding,
            )
        val density = LocalDensity.current
        val targetMinHeightPx = with(density) { activeInteractionMinHeight.toPx() }
        val itemSpacingPx = with(density) { itemSpacing.toPx() }
        val heightTracker = remember(chatId) { MessageListHeightTracker() }
        var heightLayoutGeneration by remember(chatId) { mutableIntStateOf(0) }

        val activeTurnMessageHeightKeys =
            if (latestUserMessageIndex != -1) {
                messages.subList(latestUserMessageIndex, messages.size).map { message ->
                    message.messageListHeightKey()
                }
            } else {
                emptyList()
            }
        val activeTurnContentHeightPx =
            remember(
                heightLayoutGeneration,
                latestUserMessageIndex,
                messages.size,
                targetMinHeightPx,
                itemSpacingPx,
            ) {
                messageListActiveTurnContentHeightPx(
                    itemHeightsPx = activeTurnMessageHeightKeys.map { key -> heightTracker.get(key) ?: 0 },
                    itemSpacingPx = itemSpacingPx,
                )
            }
        val bottomSpacerHeightPx =
            remember(
                heightLayoutGeneration,
                latestUserMessageIndex,
                activeTurnContentHeightPx,
                targetMinHeightPx,
                itemSpacingPx,
            ) {
                if (latestUserMessageIndex != -1) {
                    messageListBottomSpacerHeightPx(
                        targetMinHeightPx = targetMinHeightPx,
                        activeTurnContentHeightPx = activeTurnContentHeightPx,
                        bottomSpacerSpacingPx = itemSpacingPx,
                    )
                } else {
                    0
                }
            }
        val bottomSpacerHeight = with(density) { bottomSpacerHeightPx.toDp() }

        val hasBottomSpacer = bottomSpacerHeightPx > 0
        val fabScrollTargetIndex =
            if (messages.isNotEmpty()) {
                messageListScrollTargetLazyIndex(
                    messageCount = messages.size,
                    latestUserMessageIndex = latestUserMessageIndex,
                )
            } else {
                -1
            }
        val fabVisible = rememberScrollToBottomFabVisible(listState)
        val coroutineScope = rememberCoroutineScope()

        val focusManager = LocalFocusManager.current
        val nestedScrollConnection =
            remember(focusManager) {
                object : NestedScrollConnection {
                    override fun onPreScroll(
                        available: Offset,
                        source: NestedScrollSource,
                    ): Offset {
                        if (available.y > 0f) {
                            focusManager.clearFocus()
                        }
                        return Offset.Zero
                    }
                }
            }

        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .nestedScroll(nestedScrollConnection),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(itemSpacing),
            contentPadding =
                PaddingValues(
                    top = 0.dp,
                    bottom = bottomContentPadding,
                ),
        ) {
            items(
                count = messages.size,
                key = { index -> messages[index].messageListHeightKey() },
                contentType = { index -> messages[index].role },
            ) { index ->
                val message = messages[index]
                val heightKey = message.messageListHeightKey()
                val heightModifier =
                    remember(heightKey) {
                        Modifier.onSizeChanged { size ->
                            if (heightTracker.onSizeChanged(heightKey, size.height)) {
                                heightLayoutGeneration++
                            }
                        }
                    }
                MessageListItem(
                    message = message,
                    activeToolCallBanner =
                        resolveActiveToolCallBanner(
                            message = message,
                            activeIndicatorMessageId = activeIndicatorMessageId,
                            activeToolCallBanner = activeToolCallBanner,
                        ),
                    hasTtsProviderAssigned = hasTtsProviderAssigned,
                    chatMode = chatMode,
                    callbacks = callbacks,
                    isPreview = isPreview,
                    modifier = heightModifier,
                )
            }

            if (hasBottomSpacer) {
                item(
                    key = "bottom_spacer",
                    contentType = "bottom_spacer",
                ) {
                    Spacer(modifier = Modifier.height(bottomSpacerHeight))
                }
            }
        }

        ScrollToBottomFab(
            visible = fabVisible,
            onClick = {
                if (fabScrollTargetIndex >= 0) {
                    coroutineScope.launch {
                        listState.animateScrollToItem(fabScrollTargetIndex)
                    }
                }
            },
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = 12.dp),
        )
    }
}

private fun ChatMessage.messageListHeightKey(): String = "${chatId.value}:${id.value}"
