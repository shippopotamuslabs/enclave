package com.browntowndev.enclave.domain.model.config

import com.browntowndev.enclave.domain.model.inference.ModelType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MediaTaskTest {
    @Test
    fun `MASK-01 empty set maps to mask zero`() {
        assertEquals(0, emptySet<MediaTask>().toMask())
    }

    @Test
    fun `MASK-02 mask zero maps to empty set`() {
        assertEquals(emptySet<MediaTask>(), 0.toMediaTasks())
    }

    @Test
    fun `MASK-03 all four tasks map to mask fifteen`() {
        assertEquals(15, MediaTask.entries.toSet().toMask())
    }

    @Test
    fun `MASK-04 mask fifteen maps to all four tasks`() {
        val result = 15.toMediaTasks()

        assertEquals(
            setOf(MediaTask.TEXT_TO_IMAGE, MediaTask.EDIT_IMAGE, MediaTask.TEXT_TO_VIDEO, MediaTask.IMAGE_TO_VIDEO),
            result,
        )
        assertEquals(4, result.size)
    }

    @Test
    fun `MASK-05 single-bit round-trips per task`() {
        val examples =
            listOf(
                MediaTask.TEXT_TO_IMAGE to 1,
                MediaTask.EDIT_IMAGE to 2,
                MediaTask.TEXT_TO_VIDEO to 4,
                MediaTask.IMAGE_TO_VIDEO to 8,
            )

        examples.forEach { (task, bit) ->
            val mask = setOf(task).toMask()

            assertEquals(bit, mask)
            assertEquals(setOf(task), mask.toMediaTasks())
        }
    }

    @Test
    fun `MASK-06 unknown high bit is ignored and low bits are preserved`() {
        assertEquals(setOf(MediaTask.TEXT_TO_IMAGE, MediaTask.EDIT_IMAGE), 19.toMediaTasks())
    }

    @Test
    fun `MAP-01 MediaTask maps to its default-model slot`() {
        assertEquals(ModelType.IMAGE_GENERATION, MediaTask.TEXT_TO_IMAGE.toModelType())
        assertEquals(ModelType.IMAGE_EDIT, MediaTask.EDIT_IMAGE.toModelType())
        assertEquals(ModelType.VIDEO_GENERATION, MediaTask.TEXT_TO_VIDEO.toModelType())
        assertEquals(ModelType.IMAGE_TO_VIDEO, MediaTask.IMAGE_TO_VIDEO.toModelType())
    }

    @Test
    fun `MAP-02 ModelType maps back to its media task`() {
        assertEquals(MediaTask.TEXT_TO_IMAGE, ModelType.IMAGE_GENERATION.toMediaTaskOrNull())
        assertEquals(MediaTask.EDIT_IMAGE, ModelType.IMAGE_EDIT.toMediaTaskOrNull())
        assertEquals(MediaTask.TEXT_TO_VIDEO, ModelType.VIDEO_GENERATION.toMediaTaskOrNull())
        assertEquals(MediaTask.IMAGE_TO_VIDEO, ModelType.IMAGE_TO_VIDEO.toMediaTaskOrNull())
    }

    @Test
    fun `MAP-03 non-media model slots have no media task`() {
        assertNull(ModelType.MAIN.toMediaTaskOrNull())
        assertNull(ModelType.MUSIC_GENERATION.toMediaTaskOrNull())
    }

    @Test
    fun `MODELTYPE-01 new ModelType api values round-trip`() {
        assertEquals("image_edit", ModelType.IMAGE_EDIT.apiValue)
        assertEquals("image_to_video", ModelType.IMAGE_TO_VIDEO.apiValue)
        assertEquals(ModelType.IMAGE_EDIT, ModelType.fromApiValue("image_edit"))
        assertEquals(ModelType.IMAGE_TO_VIDEO, ModelType.fromApiValue("image_to_video"))
    }

    @Test
    fun `LEGACY-01 unknown mask seeds all tasks for image capability`() {
        assertEquals(
            setOf(MediaTask.TEXT_TO_IMAGE, MediaTask.EDIT_IMAGE),
            0.toMediaTasksOrLegacy(MediaCapability.IMAGE),
        )
        assertEquals(
            setOf(MediaTask.TEXT_TO_VIDEO, MediaTask.IMAGE_TO_VIDEO),
            0.toMediaTasksOrLegacy(MediaCapability.VIDEO),
        )
        assertEquals(emptySet<MediaTask>(), 0.toMediaTasksOrLegacy(MediaCapability.MUSIC))
    }

    @Test
    fun `MODELTYPE-02 new ModelType entries are not text models and have display names`() {
        assertFalse(ModelType.IMAGE_EDIT.isTextModel)
        assertFalse(ModelType.IMAGE_TO_VIDEO.isTextModel)
        assertEquals("Image Edit", ModelType.IMAGE_EDIT.displayName())
        assertEquals("Image to Video", ModelType.IMAGE_TO_VIDEO.displayName())
    }
}
