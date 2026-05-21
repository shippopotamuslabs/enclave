package com.browntowndev.pocketcrew.feature.chat.components.messagelist

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

@Composable
internal fun MessageListScrollToLatestEffect(
    chatId: ChatId?,
    messagesNotEmpty: Boolean,
    latestUserMessageId: MessageId?,
    latestUserMessageIndex: Int,
    messageCount: Int,
    listState: LazyListState,
) {
    var initialScrollDone by remember(chatId) { mutableStateOf(value = false) }
    var lastScrolledUserMessageId by remember(chatId) { mutableStateOf<MessageId?>(null) }

    LaunchedEffect(
        chatId,
        messagesNotEmpty,
        latestUserMessageId,
        latestUserMessageIndex,
        messageCount,
    ) {
        when (
            val action =
                resolveMessageListProgrammaticScrollAction(
                    initialScrollDone = initialScrollDone,
                    messagesNotEmpty = messagesNotEmpty,
                    latestUserMessageId = latestUserMessageId,
                    lastScrolledUserMessageId = lastScrolledUserMessageId,
                    latestUserMessageIndex = latestUserMessageIndex,
                    messageCount = messageCount,
                )
        ) {
            MessageListProgrammaticScrollAction.None -> Unit
            is MessageListProgrammaticScrollAction.ScrollToUserTurn -> {
                if (action.animated) {
                    withFrameNanos { }
                    listState.animateScrollToItem(action.lazyIndex)
                } else {
                    listState.scrollToItem(action.lazyIndex)
                }
                initialScrollDone = true
                lastScrolledUserMessageId = latestUserMessageId
            }
        }
    }
}

@Composable
internal fun rememberScrollToBottomFabVisible(listState: LazyListState): Boolean {
    var fabVisible by remember { mutableStateOf(value = false) }
    var fabAutoHideSignal by remember { mutableIntStateOf(0) }

    LaunchedEffect(listState) {
        snapshotFlow {
            listState.isScrollInProgress to listState.canScrollForward
        }.collect { (isScrolling, canScrollForward) ->
            when (
                val update =
                    resolveMessageListFabVisibilityUpdate(
                        isScrollInProgress = isScrolling,
                        canScrollForward = canScrollForward,
                    )
            ) {
                is MessageListFabVisibilityUpdate.SetVisible -> fabVisible = update.visible
                MessageListFabVisibilityUpdate.ScheduleAutoHide -> fabAutoHideSignal++
            }
        }
    }
    LaunchedEffect(fabAutoHideSignal) {
        if (fabAutoHideSignal > 0) {
            delay(3.seconds)
            fabVisible = false
        }
    }
    return fabVisible
}
