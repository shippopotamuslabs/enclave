package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.usecase.FakeInferenceFactory
import com.browntowndev.pocketcrew.domain.usecase.FakeInferenceService
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnalyzeImageUseCaseTest {

    @Test
    fun `invoke uses the vision slot and forwards image uris`() = runTest {
        val factory = FakeInferenceFactory()
        val visionService = FakeInferenceService(ModelType.VISION).apply {
            setEmittedEvents(
                listOf(
                    InferenceEvent.PartialResponse("A cat sleeping on a sofa.", ModelType.VISION),
                    InferenceEvent.Finished(ModelType.VISION),
                )
            )
        }
        factory.serviceMap[ModelType.VISION] = visionService

        val result = AnalyzeImageUseCase(factory, mockk(relaxed = true))(
            imageUri = "file:///tmp/test-image.jpg",
            prompt = "What is happening here?",
        )

        assertEquals("A cat sleeping on a sofa.", result)
        assertEquals(ModelType.VISION, factory.resolvedTypes.single())
        assertEquals(listOf("file:///tmp/test-image.jpg"), visionService.getSentOptions().single().imageUris)
        assertTrue(visionService.getSentPrompts().single().contains("User request: What is happening here?"))
    }
}
