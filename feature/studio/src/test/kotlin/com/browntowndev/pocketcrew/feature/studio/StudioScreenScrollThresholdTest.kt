package com.browntowndev.pocketcrew.feature.studio

import com.browntowndev.pocketcrew.domain.model.config.MediaCapability
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StudioScreenScrollThresholdTest {

    @Test
    fun thresholdVisible_whenVisibleRowsAreAboveSecondToLast_returnsFalse() {
        val galleryRowKeys = listOf("row-1", "row-2", "row-3", "row-4")
        val visibleItemKeys = listOf("studio_header_prompt", "row-1", "row-2")

        val result = isGenerativeScrollThresholdVisible(
            galleryRowKeys = galleryRowKeys,
            visibleItemKeys = visibleItemKeys,
        )

        assertFalse(result)
    }

    @Test
    fun thresholdVisible_whenSecondToLastGalleryRowIsVisible_returnsTrue() {
        val galleryRowKeys = listOf("row-1", "row-2", "row-3", "row-4")
        val visibleItemKeys = listOf("row-3")

        val result = isGenerativeScrollThresholdVisible(
            galleryRowKeys = galleryRowKeys,
            visibleItemKeys = visibleItemKeys,
        )

        assertTrue(result)
    }

    @Test
    fun thresholdVisible_whenLastGalleryRowIsVisible_returnsTrue() {
        val galleryRowKeys = listOf("row-1", "row-2", "row-3", "row-4")
        val visibleItemKeys = listOf("row-4")

        val result = isGenerativeScrollThresholdVisible(
            galleryRowKeys = galleryRowKeys,
            visibleItemKeys = visibleItemKeys,
        )

        assertTrue(result)
    }

    @Test
    fun thresholdVisible_whenOnlyHeaderAndPlaceholderAreVisible_returnsFalse() {
        val galleryRowKeys = listOf("row-1", "row-2", "row-3")
        val visibleItemKeys = listOf("studio_header_prompt", "studio_placeholder_row_0")

        val result = isGenerativeScrollThresholdVisible(
            galleryRowKeys = galleryRowKeys,
            visibleItemKeys = visibleItemKeys,
        )

        assertFalse(result)
    }

    @Test
    fun thresholdVisible_whenSingleGalleryRowIsVisible_returnsTrue() {
        val galleryRowKeys = listOf("row-1")
        val visibleItemKeys = listOf("row-1")

        val result = isGenerativeScrollThresholdVisible(
            galleryRowKeys = galleryRowKeys,
            visibleItemKeys = visibleItemKeys,
        )

        assertTrue(result)
    }

    @Test
    fun galleryRows_whenLastMediaRowHasEmptySlot_addsPlaceholderToSameRow() {
        val group = StudioGalleryGroup(
            prompt = "prompt",
            items = listOf(studioMedia("asset-1")),
        )

        val rows = buildStudioGalleryRows(
            group = group,
            placeholderCount = 1,
        )

        assertEquals(1, rows.size)
        assertEquals(listOf("asset-1"), rows[0].mediaItems.map { it.id })
        assertEquals(1, rows[0].placeholderCount)
    }

    @Test
    fun galleryRows_whenLastMediaRowHasOneSlot_addsOverflowPlaceholderToNewRow() {
        val group = StudioGalleryGroup(
            prompt = "prompt",
            items = listOf(studioMedia("asset-1")),
        )

        val rows = buildStudioGalleryRows(
            group = group,
            placeholderCount = 2,
        )

        assertEquals(2, rows.size)
        assertEquals(listOf("asset-1"), rows[0].mediaItems.map { it.id })
        assertEquals(1, rows[0].placeholderCount)
        assertEquals(emptyList<StudioMediaUi>(), rows[1].mediaItems)
        assertEquals(1, rows[1].placeholderCount)
    }

    @Test
    fun galleryRows_whenLastMediaRowIsFull_addsPlaceholderToNewRow() {
        val group = StudioGalleryGroup(
            prompt = "prompt",
            items = listOf(studioMedia("asset-1"), studioMedia("asset-2")),
        )

        val rows = buildStudioGalleryRows(
            group = group,
            placeholderCount = 1,
        )

        assertEquals(2, rows.size)
        assertEquals(listOf("asset-1", "asset-2"), rows[0].mediaItems.map { it.id })
        assertEquals(0, rows[0].placeholderCount)
        assertEquals(emptyList<StudioMediaUi>(), rows[1].mediaItems)
        assertEquals(1, rows[1].placeholderCount)
    }

    private fun studioMedia(id: String): StudioMediaUi =
        StudioMediaUi(
            id = id,
            localUri = "content://$id",
            prompt = "prompt",
            mediaType = MediaCapability.IMAGE,
            createdAt = 1L,
        )
}
