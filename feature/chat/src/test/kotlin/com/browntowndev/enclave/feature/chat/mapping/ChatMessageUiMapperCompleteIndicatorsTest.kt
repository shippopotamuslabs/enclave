package com.browntowndev.enclave.feature.chat.mapping

import com.browntowndev.enclave.domain.model.artifact.ArtifactGenerationRequest
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.Content
import com.browntowndev.enclave.domain.model.chat.Message
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.MessageState
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.feature.chat.IndicatorState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChatMessageUiMapperCompleteIndicatorsTest {
    private val mapper = ChatMessageUiMapper()
    private val displayNames = mapOf(ModelType.FAST to "Fast")

    private val sampleArtifact =
        ArtifactGenerationRequest(
            artifactId = "artifact-1",
            title = "Notes",
            content = "{\"blocks\":[]}",
        )

    @Test
    fun `incomplete assistant hides artifacts and uses generating indicator`() {
        val message =
            Message(
                id = MessageId("a1"),
                chatId = ChatId("chat-1"),
                role = Role.ASSISTANT,
                content =
                    Content(
                        text = "partial",
                        artifacts = listOf(sampleArtifact),
                    ),
                messageState = MessageState.GENERATING,
                modelType = ModelType.FAST,
            )

        val ui = mapper.map(message, displayNames)

        assertTrue(ui.content.artifacts.isEmpty())
        assertTrue(ui.indicatorState is IndicatorState.Generating)
    }

    @Test
    fun `complete assistant exposes artifacts and complete indicator`() {
        val message =
            Message(
                id = MessageId("a1"),
                chatId = ChatId("chat-1"),
                role = Role.ASSISTANT,
                content =
                    Content(
                        text = "done",
                        artifacts = listOf(sampleArtifact),
                    ),
                messageState = MessageState.COMPLETE,
                modelType = ModelType.FAST,
                thinkingRaw = "thought",
                thinkingStartTime = 1_000L,
                thinkingDurationSeconds = 3L,
            )

        val ui = mapper.map(message, displayNames)

        assertEquals(1, ui.content.artifacts.size)
        assertEquals(
            "Notes",
            ui.content.artifacts
                .first()
                .title,
        )
        assertTrue(ui.indicatorState is IndicatorState.Complete)
        val complete = ui.indicatorState as IndicatorState.Complete
        assertEquals(3L, complete.thinkingData?.thinkingDurationSeconds)
    }
}
