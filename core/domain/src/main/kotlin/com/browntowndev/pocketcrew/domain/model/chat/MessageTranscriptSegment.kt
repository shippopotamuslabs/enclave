package com.browntowndev.pocketcrew.domain.model.chat

/**
 * One ordered piece of an assistant message transcript: either a CoT run or visible answer text.
 * [ordinal] is 0-based and strictly increasing per message.
 */
sealed class MessageTranscriptSegment {
    abstract val ordinal: Int

    data class Thinking(
        override val ordinal: Int,
        val text: String,
        val thinkingDurationSeconds: Long? = null,
        val thinkingStartTime: Long? = null,
        val thinkingEndTime: Long? = null,
    ) : MessageTranscriptSegment()

    data class AssistantText(
        override val ordinal: Int,
        val text: String,
    ) : MessageTranscriptSegment()
}
