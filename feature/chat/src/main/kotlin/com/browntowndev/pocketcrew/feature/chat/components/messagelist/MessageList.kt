package com.browntowndev.pocketcrew.feature.chat.components.messagelist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.feature.chat.ArtifactCardUiModel
import com.browntowndev.pocketcrew.feature.chat.ChatMessage
import com.browntowndev.pocketcrew.feature.chat.ChatModeUi
import com.browntowndev.pocketcrew.feature.chat.MessageRole
import com.browntowndev.pocketcrew.feature.chat.ToolCallBannerUi
import kotlinx.coroutines.launch

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
    val callbacks =
        MessageListCallbacks(
            onEditMessage = onEditMessage,
            onPlayAudio = onPlayAudio,
            onArtifactCardClick = onArtifactCardClick,
            onNavigateToStudio = onNavigateToStudio,
            onShareClick = onShareClick,
        )
    val listState = rememberLazyListState()
    val suppressScrollAnchor = remember { mutableStateOf(false) }

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
        suppressScrollAnchor = suppressScrollAnchor,
    )

    if (messages.isEmpty() && !hasActiveIndicator) {
        MessageListEmptyState(modifier = modifier)
    } else {
        MessageListContent(
            modifier = modifier,
            messages = messages,
            latestUserMessageIndex = latestUserMessageIndex,
            latestUserMessageId = latestUserMessageId,
            listState = listState,
            suppressScrollAnchor = suppressScrollAnchor,
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
    latestUserMessageIndex: Int,
    latestUserMessageId: MessageId?,
    listState: LazyListState,
    suppressScrollAnchor: MutableState<Boolean>,
    activeIndicatorMessageId: MessageId?,
    activeToolCallBanner: ToolCallBannerUi?,
    hasTtsProviderAssigned: Boolean,
    chatMode: ChatModeUi,
    callbacks: MessageListCallbacks,
    isPreview: Boolean,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val bottomContentPadding = 16.dp
        val activeInteractionMinHeight =
            activeInteractionMinHeight(
                viewportHeight = maxHeight,
                bottomContentPadding = bottomContentPadding,
            )
        val density = LocalDensity.current
        val targetMinHeightPx = with(density) { activeInteractionMinHeight.toPx() }
        val lazyItemSpacingPx = with(density) { 12.dp.toPx() }

        var activeUserHeightPx by remember(latestUserMessageId) { mutableIntStateOf(0) }
        val activeAssistantHeightsPx =
            remember(latestUserMessageId) { mutableStateMapOf<MessageId, Int>() }

        val activeAssistantCount =
            if (latestUserMessageIndex != -1) {
                messages.size - latestUserMessageIndex - 1
            } else {
                0
            }

        val activeAssistantTailHeightPx =
            messageListActiveAssistantTailHeightPx(
                assistantHeightsPx = activeAssistantHeightsPx.values,
                lazyItemSpacingPx = lazyItemSpacingPx,
            )

        val fillerHeightPx =
            messageListActiveViewportFillerHeightPx(
                targetMinHeightPx = targetMinHeightPx,
                userHeightPx = activeUserHeightPx,
                assistantTailHeightPx = activeAssistantTailHeightPx,
                lazyItemSpacingPx = lazyItemSpacingPx,
                activeAssistantCount = activeAssistantCount,
            )

        MessageListScrollAnchorEffect(
            listState = listState,
            fillerHeightPx = fillerHeightPx,
            suppressScrollAnchor = suppressScrollAnchor,
        )

        val totalItems =
            messageListTotalLazyItems(
                messageCount = messages.size,
                latestUserMessageIndex = latestUserMessageIndex,
            )
        val lastLazyItemIndex = totalItems - 1
        val fabVisible = rememberScrollToBottomFabVisible(listState)
        val coroutineScope = rememberCoroutineScope()

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 0.dp, bottom = bottomContentPadding),
        ) {
            val historyCount = if (latestUserMessageIndex != -1) latestUserMessageIndex else messages.size
            items(
                count = historyCount,
                key = { index -> "history_msg_${messages[index].id}" },
            ) { index ->
                MessageListItem(
                    message = messages[index],
                    activeIndicatorMessageId = activeIndicatorMessageId,
                    activeToolCallBanner = activeToolCallBanner,
                    hasTtsProviderAssigned = hasTtsProviderAssigned,
                    chatMode = chatMode,
                    callbacks = callbacks,
                    isPreview = isPreview,
                )
            }

            if (latestUserMessageIndex != -1 && latestUserMessageId != null) {
                item(key = "active_user_$latestUserMessageId") {
                    MessageListItem(
                        message = messages[latestUserMessageIndex],
                        activeIndicatorMessageId = activeIndicatorMessageId,
                        activeToolCallBanner = activeToolCallBanner,
                        hasTtsProviderAssigned = hasTtsProviderAssigned,
                        chatMode = chatMode,
                        callbacks = callbacks,
                        isPreview = isPreview,
                        modifier =
                            Modifier.onSizeChanged { size ->
                                activeUserHeightPx = size.height
                            },
                    )
                }

                item(key = "active_viewport_filler_$latestUserMessageId") {
                    MessageListActiveViewportFiller(heightPx = fillerHeightPx)
                }

                val assistantStartIndex = latestUserMessageIndex + 1
                items(
                    count = activeAssistantCount,
                    key = { offset -> "active_assistant_${messages[assistantStartIndex + offset].id}" },
                ) { offset ->
                    val messageIndex = assistantStartIndex + offset
                    val message = messages[messageIndex]
                    MessageListItem(
                        message = message,
                        activeIndicatorMessageId = activeIndicatorMessageId,
                        activeToolCallBanner = activeToolCallBanner,
                        hasTtsProviderAssigned = hasTtsProviderAssigned,
                        chatMode = chatMode,
                        callbacks = callbacks,
                        isPreview = isPreview,
                        modifier =
                            Modifier.onSizeChanged { size ->
                                activeAssistantHeightsPx[message.id] = size.height
                            },
                    )
                }
            }
        }

        ScrollToBottomFab(
            visible = fabVisible,
            onClick = {
                if (lastLazyItemIndex >= 0) {
                    coroutineScope.launch { listState.animateScrollToItem(lastLazyItemIndex) }
                }
            },
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = 12.dp),
        )
    }
}
