package com.browntowndev.enclave.feature.chat.components.assistantresponse

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.browntowndev.enclave.core.ui.component.markdown.StreamableMarkdownText
import com.browntowndev.enclave.feature.chat.ChatMessage
import com.browntowndev.enclave.feature.chat.ToolCallBannerUi
import com.browntowndev.enclave.feature.chat.TranscriptSegmentUi
import com.browntowndev.enclave.feature.chat.components.ToolCallBanner
import com.browntowndev.enclave.feature.chat.components.messagelist.ThoughtForHeader
import com.browntowndev.enclave.feature.chat.components.messagelist.formatThinkingDuration

@Composable
internal fun AssistantResponseTranscript(
    message: ChatMessage,
    contentText: String,
    isStreaming: Boolean,
    isPreview: Boolean,
    toolCallBanner: ToolCallBannerUi?,
    onOpenThinkingSheet: (
        text: String,
        durationSeconds: Long?,
        streaming: Boolean,
        elapsedForComplete: Int,
    ) -> Unit,
) {
    val segments = message.transcriptSegments
    if (segments.isEmpty()) {
        StreamableMarkdownText(
            markdown = contentText,
            modifier = Modifier.fillMaxWidth(),
            enableScroll = false,
            isStreaming = isStreaming,
            isPreview = isPreview,
            bodyStyle = AssistantResponseBodyTextStyle,
        )
        return
    }

    val insertAfterIndex = toolCallBannerInsertAfterSegmentIndex(segments)
    val showBannerBeforeSegments = toolCallBanner != null && insertAfterIndex == null
    if (showBannerBeforeSegments) {
        ToolCallBanner(
            banner = toolCallBanner,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }

    segments.forEachIndexed { index, segment ->
        key(segment.ordinal) {
            when (segment) {
                is TranscriptSegmentUi.ThinkingBlock -> {
                    if (segment.isStreaming) {
                        LiveThinkingTranscriptRow(
                            segment = segment,
                            isPreview = isPreview,
                            onToggleDetails = {
                                onOpenThinkingSheet(
                                    segment.text,
                                    null,
                                    true,
                                    0,
                                )
                            },
                        )
                    } else {
                        ThoughtForHeader(
                            durationText =
                                formatThinkingDuration(
                                    (segment.durationSeconds ?: 0L).toInt().coerceAtLeast(0),
                                ),
                            isExpanded = false,
                            onViewFullThinking = {
                                onOpenThinkingSheet(
                                    segment.text,
                                    segment.durationSeconds,
                                    false,
                                    (segment.durationSeconds ?: 0L).toInt().coerceAtLeast(0),
                                )
                            },
                        )
                    }
                }
                is TranscriptSegmentUi.AnswerBlock -> {
                    StreamableMarkdownText(
                        markdown = segment.text,
                        modifier = Modifier.fillMaxWidth(),
                        enableScroll = false,
                        isStreaming = segment.isStreaming && isStreaming,
                        isPreview = isPreview,
                        bodyStyle = AssistantResponseBodyTextStyle,
                    )
                }
            }
        }
        if (toolCallBanner != null && insertAfterIndex == index) {
            ToolCallBanner(
                banner = toolCallBanner,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
    }
}
