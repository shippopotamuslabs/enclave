package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.domain.model.artifact.ArtifactGenerationRequest
import com.browntowndev.pocketcrew.domain.model.artifact.MimeType
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.WebSearchSource
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class MessageAccumulatorSnapshotSharingTest {

    @Test
    fun `toSnapshot reuses list references when accumulator collections are unchanged`() {
        val accumulator = populatedAccumulator()

        val first = accumulator.toSnapshot()
        val second = accumulator.toSnapshot()

        assertSame(first.webSearchSources, second.webSearchSources)
        assertSame(first.artifacts, second.artifacts)
        assertSame(first.generatedMediaUris, second.generatedMediaUris)
        assertSame(first.generatedMediaAssetIds, second.generatedMediaAssetIds)
        assertSame(first.generatedMediaTypes, second.generatedMediaTypes)
        assertSame(first.generatedMediaPrompts, second.generatedMediaPrompts)
        assertSame(first.transcriptSegments, second.transcriptSegments)
    }

    @Test
    fun `mutating artifacts reallocates artifacts list and reuses unchanged list references`() {
        val accumulator = populatedAccumulator()
        val first = accumulator.toSnapshot()

        accumulator.artifacts.add(artifact(id = "artifact-2", title = "Second"))
        val second = accumulator.toSnapshot()

        assertNotSame(first.artifacts, second.artifacts)
        assertSame(first.webSearchSources, second.webSearchSources)
        assertSame(first.generatedMediaUris, second.generatedMediaUris)
        assertSame(first.generatedMediaAssetIds, second.generatedMediaAssetIds)
        assertSame(first.generatedMediaTypes, second.generatedMediaTypes)
        assertSame(first.generatedMediaPrompts, second.generatedMediaPrompts)
        assertSame(first.transcriptSegments, second.transcriptSegments)
    }

    private fun populatedAccumulator(): MessageAccumulator {
        return MessageAccumulator(
            messageId = MessageId("assistant"),
            modelType = ModelType.FAST,
        ).apply {
            webSearchSources.add(
                WebSearchSource(
                    messageId = MessageId("assistant"),
                    title = "Source",
                    url = "https://example.com/source",
                    content = "source content",
                )
            )
            artifacts.add(artifact(id = "artifact-1", title = "First"))
            generatedMediaUris.add("content://media/1")
            generatedMediaAssetIds.add("media-1")
            generatedMediaTypes.add("image")
            generatedMediaPrompts.add("draw it")
            appendContent("answer")
        }
    }

    private fun artifact(
        id: String,
        title: String,
    ): ArtifactGenerationRequest {
        return ArtifactGenerationRequest(
            artifactId = id,
            title = title,
            targetFormat = MimeType.PDF,
            content = "content",
        )
    }
}
