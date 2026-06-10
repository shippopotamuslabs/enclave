package com.browntowndev.enclave.feature.inference

import com.anthropic.models.messages.RawMessageStopEvent
import com.anthropic.models.messages.RawMessageStreamEvent
import io.mockk.every
import io.mockk.mockk
import java.util.Optional

/**
 * Terminal `message_stop` event so mocked Anthropic streams pass the
 * stream-disconnect guard in [AnthropicInferenceServiceImpl].
 */
internal fun anthropicMessageStopEvent(): RawMessageStreamEvent {
    val event = mockk<RawMessageStreamEvent>(relaxed = true)
    every { event.messageStop() } returns Optional.of(mockk<RawMessageStopEvent>(relaxed = true))
    return event
}
