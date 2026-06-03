package com.browntowndev.enclave.feature.inference.fal

import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.config.MediaTask
import com.browntowndev.enclave.domain.model.media.ImageInputRequirement
import com.browntowndev.enclave.domain.model.media.ModelGenerationInputSchema
import com.browntowndev.enclave.feature.inference.repository.deriveFalMediaTasks
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FalMediaTasksRedTest {
    @Test
    fun `PARSE-01 image field absent yields imageInput NONE`() {
        val schema = FalOpenApiInputSchemaParser.parse(openApi(imageField = null, required = listOf("prompt")))

        assertEquals(ImageInputRequirement.NONE, schema?.imageInput)
    }

    @Test
    fun `PARSE-02 required image field yields imageInput REQUIRED`() {
        val schema = FalOpenApiInputSchemaParser.parse(openApi(imageField = "image_url", required = listOf("prompt", "image_url")))

        assertEquals(ImageInputRequirement.REQUIRED, schema?.imageInput)
    }

    @Test
    fun `PARSE-03 optional image field yields imageInput OPTIONAL`() {
        val schema = FalOpenApiInputSchemaParser.parse(openApi(imageField = "image_url", required = listOf("prompt")))

        assertEquals(ImageInputRequirement.OPTIONAL, schema?.imageInput)
    }

    @Test
    fun `PARSE-04 derives image tasks from all schema rows`() {
        assertEquals(
            setOf(MediaTask.TEXT_TO_IMAGE),
            deriveFalMediaTasks(MediaCapability.IMAGE, ModelGenerationInputSchema(imageInput = ImageInputRequirement.NONE)),
        )
        assertEquals(
            setOf(MediaTask.EDIT_IMAGE),
            deriveFalMediaTasks(MediaCapability.IMAGE, ModelGenerationInputSchema(imageInput = ImageInputRequirement.REQUIRED)),
        )
        assertEquals(
            setOf(MediaTask.TEXT_TO_IMAGE, MediaTask.EDIT_IMAGE),
            deriveFalMediaTasks(MediaCapability.IMAGE, ModelGenerationInputSchema(imageInput = ImageInputRequirement.OPTIONAL)),
        )
    }

    @Test
    fun `PARSE-05 derives video tasks from all schema rows`() {
        assertEquals(
            setOf(MediaTask.TEXT_TO_VIDEO),
            deriveFalMediaTasks(MediaCapability.VIDEO, ModelGenerationInputSchema(imageInput = ImageInputRequirement.NONE)),
        )
        assertEquals(
            setOf(MediaTask.IMAGE_TO_VIDEO),
            deriveFalMediaTasks(MediaCapability.VIDEO, ModelGenerationInputSchema(imageInput = ImageInputRequirement.REQUIRED)),
        )
        assertEquals(
            setOf(MediaTask.TEXT_TO_VIDEO, MediaTask.IMAGE_TO_VIDEO),
            deriveFalMediaTasks(MediaCapability.VIDEO, ModelGenerationInputSchema(imageInput = ImageInputRequirement.OPTIONAL)),
        )
    }

    @Test
    fun `PARSE-06 optional image advertises both image tasks`() {
        val result =
            deriveFalMediaTasks(MediaCapability.IMAGE, ModelGenerationInputSchema(imageInput = ImageInputRequirement.OPTIONAL))

        assertEquals(2, result.size)
        assertTrue(MediaTask.TEXT_TO_IMAGE in result)
        assertTrue(MediaTask.EDIT_IMAGE in result)
    }

    @Test
    fun `CATALOG-01 text-to-image category maps to text-to-image task`() {
        assertEquals(
            setOf(MediaTask.TEXT_TO_IMAGE),
            deriveFalMediaTasks(MediaCapability.IMAGE, null, "fal-ai/recraft/v3", "text-to-image"),
        )
    }

    @Test
    fun `CATALOG-02 image-to-image category maps to edit-image task`() {
        assertEquals(
            setOf(MediaTask.EDIT_IMAGE),
            deriveFalMediaTasks(MediaCapability.IMAGE, null, "fal-ai/flux-pro/kontext/edit", "image-to-image"),
        )
    }

    @Test
    fun `CATALOG-03 text-to-video category maps to text-to-video task`() {
        assertEquals(
            setOf(MediaTask.TEXT_TO_VIDEO),
            deriveFalMediaTasks(MediaCapability.VIDEO, null, "bytedance/seedance-2.0/text-to-video", "text-to-video"),
        )
    }

    @Test
    fun `CATALOG-04 image-to-video category maps to image-to-video task`() {
        assertEquals(
            setOf(MediaTask.IMAGE_TO_VIDEO),
            deriveFalMediaTasks(MediaCapability.VIDEO, null, "fal-ai/kling-video/v2.1/standard/image-to-video", "image-to-video"),
        )
    }

    @Test
    fun `CATALOG-05 endpoint substring drives image-to-video task when category is absent`() {
        assertEquals(
            setOf(MediaTask.IMAGE_TO_VIDEO),
            deriveFalMediaTasks(MediaCapability.VIDEO, null, "fal-ai/wan/v2.2-a14b/image-to-video/lora", null),
        )
    }

    @Test
    fun `CATALOG-06 featured ids fall back to maintained task sets`() {
        val examples =
            mapOf(
                "fal-ai/flux-pro/kontext" to setOf(MediaTask.TEXT_TO_IMAGE, MediaTask.EDIT_IMAGE),
                "fal-ai/recraft/v3" to setOf(MediaTask.TEXT_TO_IMAGE),
                "fal-ai/kling-video/v2.1/master/image-to-video" to setOf(MediaTask.IMAGE_TO_VIDEO),
                "fal-ai/minimax/video-01" to setOf(MediaTask.TEXT_TO_VIDEO),
            )

        examples.forEach { (id, tasks) ->
            assertEquals(tasks, deriveFalMediaTasks(MediaCapability.IMAGE, null, id, null))
        }
    }

    @Test
    fun `MUT-02 optional video image input advertises both video tasks`() {
        val result =
            deriveFalMediaTasks(MediaCapability.VIDEO, ModelGenerationInputSchema(imageInput = ImageInputRequirement.OPTIONAL))

        assertEquals(2, result.size)
        assertTrue(MediaTask.TEXT_TO_VIDEO in result)
        assertTrue(MediaTask.IMAGE_TO_VIDEO in result)
        assertTrue(result != setOf(MediaTask.IMAGE_TO_VIDEO))
    }

    private fun openApi(
        imageField: String?,
        required: List<String>,
    ): JsonObject {
        val imageProperty =
            imageField?.let { ""","$it":{"type":"string"}""" }.orEmpty()
        val requiredJson = required.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
        return Json.parseToJsonElement(
            """
            {
              "components": {
                "schemas": {
                  "Input": {
                    "type": "object",
                    "required": $requiredJson,
                    "properties": {
                      "prompt": {"type": "string"}$imageProperty
                    }
                  }
                }
              }
            }
            """.trimIndent(),
        ) as JsonObject
    }
}
