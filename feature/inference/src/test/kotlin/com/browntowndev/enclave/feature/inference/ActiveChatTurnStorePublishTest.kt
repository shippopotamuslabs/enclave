package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.model.artifact.ArtifactGenerationRequest
import com.browntowndev.enclave.domain.model.artifact.MimeType
import com.browntowndev.enclave.domain.model.chat.AccumulatedMessages
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.MessageSnapshot
import com.browntowndev.enclave.domain.model.chat.MessageState
import com.browntowndev.enclave.domain.model.chat.MessageTranscriptSegment
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.inference.ActiveChatTurnKey
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class ActiveChatTurnStorePublishTest {
    private val key =
        ActiveChatTurnKey(
            chatId = ChatId("chat"),
            assistantMessageId = MessageId("A"),
        )

    @Test
    fun `publish update for B preserves prior MessageSnapshot reference for A`() =
        runTest {
            val store = ActiveChatTurnStore()
            val messageA = snapshot(id = MessageId("A"), content = "A")
            val messageB = snapshot(id = MessageId("B"), content = "B")

            store.publish(key, AccumulatedMessages(messages = mapOf(MessageId("A") to messageA)))
            val priorA = store.snapshotValue(key)?.messages?.get(MessageId("A"))

            store.publish(key, AccumulatedMessages(messages = mapOf(MessageId("B") to messageB)))

            assertSame(priorA, store.snapshotValue(key)?.messages?.get(MessageId("A")))
        }

    @Test
    fun `publish with no effective terminal message changes preserves MessageSnapshot reference`() =
        runTest {
            val store = ActiveChatTurnStore()
            val existing =
                snapshot(
                    id = MessageId("A"),
                    content = "A",
                    isComplete = true,
                    messageState = MessageState.COMPLETE,
                    artifacts = listOf(artifact()),
                    generatedMediaUris = listOf("content://media/1"),
                    generatedMediaAssetIds = listOf("media-1"),
                    generatedMediaTypes = listOf("image"),
                    generatedMediaPrompts = listOf("draw it"),
                )

            store.publish(key, AccumulatedMessages(messages = mapOf(MessageId("A") to existing)))
            val prior = store.snapshotValue(key)?.messages?.get(MessageId("A"))
            store.publish(
                key,
                AccumulatedMessages(
                    messages =
                        mapOf(
                            MessageId("A") to
                                snapshot(
                                    id = MessageId("A"),
                                    content = "A",
                                    isComplete = true,
                                    messageState = MessageState.COMPLETE,
                                ),
                        ),
                ),
            )

            assertSame(prior, store.snapshotValue(key)?.messages?.get(MessageId("A")))
        }

    @Test
    fun `live publish with new transcript replaces prior snapshot`() =
        runTest {
            val store = ActiveChatTurnStore()
            val prior =
                snapshot(
                    id = MessageId("A"),
                    content = "",
                    messageState = MessageState.PROCESSING,
                )
            val updated =
                snapshot(
                    id = MessageId("A"),
                    content = "",
                    messageState = MessageState.THINKING,
                    transcriptSegments =
                        listOf(
                            MessageTranscriptSegment.Thinking(
                                ordinal = 0,
                                text = "planning",
                            ),
                        ),
                )

            store.publish(key, AccumulatedMessages(messages = mapOf(MessageId("A") to prior)))
            store.publish(key, AccumulatedMessages(messages = mapOf(MessageId("A") to updated)))

            val current = checkNotNull(store.snapshotValue(key)?.messages?.get(MessageId("A")))
            assertEquals(MessageState.THINKING, current.messageState)
            assertEquals(1, current.transcriptSegments.size)
        }

    @Test
    fun `publish with text change preserves unchanged artifact and media list references`() =
        runTest {
            val store = ActiveChatTurnStore()
            val existing =
                snapshot(
                    id = MessageId("A"),
                    content = "A",
                    artifacts = listOf(artifact()),
                    generatedMediaUris = listOf("content://media/1"),
                    generatedMediaAssetIds = listOf("media-1"),
                    generatedMediaTypes = listOf("image"),
                    generatedMediaPrompts = listOf("draw it"),
                )

            store.publish(key, AccumulatedMessages(messages = mapOf(MessageId("A") to existing)))
            val prior = checkNotNull(store.snapshotValue(key)?.messages?.get(MessageId("A")))
            store.publish(
                key,
                AccumulatedMessages(
                    messages = mapOf(MessageId("A") to snapshot(id = MessageId("A"), content = "A plus")),
                ),
            )
            val current = checkNotNull(store.snapshotValue(key)?.messages?.get(MessageId("A")))

            assertNotSame(prior, current)
            assertEquals("A plus", current.content)
            assertSame(prior.artifacts, current.artifacts)
            assertSame(prior.generatedMediaUris, current.generatedMediaUris)
            assertSame(prior.generatedMediaAssetIds, current.generatedMediaAssetIds)
            assertSame(prior.generatedMediaTypes, current.generatedMediaTypes)
            assertSame(prior.generatedMediaPrompts, current.generatedMediaPrompts)
        }

    private fun snapshot(
        id: MessageId,
        content: String,
        isComplete: Boolean = false,
        messageState: MessageState = MessageState.GENERATING,
        transcriptSegments: List<MessageTranscriptSegment> = emptyList(),
        artifacts: List<ArtifactGenerationRequest> = emptyList(),
        generatedMediaUris: List<String> = emptyList(),
        generatedMediaAssetIds: List<String> = emptyList(),
        generatedMediaTypes: List<String> = emptyList(),
        generatedMediaPrompts: List<String> = emptyList(),
    ): MessageSnapshot =
        MessageSnapshot(
            messageId = id,
            modelType = ModelType.FAST,
            content = content,
            thinkingRaw = "",
            isComplete = isComplete,
            messageState = messageState,
            transcriptSegments = transcriptSegments,
            artifacts = artifacts,
            generatedMediaUris = generatedMediaUris,
            generatedMediaAssetIds = generatedMediaAssetIds,
            generatedMediaTypes = generatedMediaTypes,
            generatedMediaPrompts = generatedMediaPrompts,
        )

    private fun artifact(): ArtifactGenerationRequest =
        ArtifactGenerationRequest(
            artifactId = "artifact-1",
            targetFormat = MimeType.PDF,
            title = "Artifact",
            content = "content",
        )
}
