package com.browntowndev.pocketcrew.feature.chat.components.assistantresponse

import com.browntowndev.pocketcrew.feature.chat.TranscriptSegmentUi

/**
 * Where to insert an active tool-call banner relative to transcript segments so it sits **below**
 * the **latest** thinking UI instead of above it in [com.browntowndev.pocketcrew.feature.chat.components.messagelist.MessageListItem].
 *
 * @return index of the segment **after which** the banner is shown, or `null` if there is no
 * thinking row yet so the banner stays above the first segment.
 */
internal fun toolCallBannerInsertAfterSegmentIndex(segments: List<TranscriptSegmentUi>): Int? {
    if (segments.isEmpty()) return null
    val lastThinkingIndex = segments.indexOfLast { it is TranscriptSegmentUi.ThinkingBlock }
    return if (lastThinkingIndex >= 0) lastThinkingIndex else null
}
