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

    @Test
    fun promptHeaders_whenGalleryHasMultipleGroups_tracksLazyListItemIndexes() {
        val headers = buildStudioPromptHeaderInfo(
            groupedGallery = listOf(
                StudioGalleryGroup(
                    prompt = "first",
                    items = listOf(studioMedia("asset-1"), studioMedia("asset-2"), studioMedia("asset-3")),
                ),
                StudioGalleryGroup(
                    prompt = "second",
                    items = listOf(studioMedia("asset-4")),
                ),
            ),
            isGenerating = false,
            activeGenerationPrompt = null,
            generationPlaceholderCount = 1,
        )

        assertEquals(
            listOf(
                StudioPromptHeaderInfo(
                    key = "studio_header_0_first",
                    prompt = "first",
                    itemIndex = 0,
                ),
                StudioPromptHeaderInfo(
                    key = "studio_header_1_second",
                    prompt = "second",
                    itemIndex = 3,
                ),
            ),
            headers,
        )
    }

    @Test
    fun promptHeaders_whenGeneratingNewPrompt_addsPlaceholderHeaderAfterExistingRows() {
        val headers = buildStudioPromptHeaderInfo(
            groupedGallery = listOf(
                StudioGalleryGroup(
                    prompt = "first",
                    items = listOf(studioMedia("asset-1")),
                ),
            ),
            isGenerating = true,
            activeGenerationPrompt = "second",
            generationPlaceholderCount = 2,
        )

        assertEquals(
            listOf(
                StudioPromptHeaderInfo(
                    key = "studio_header_0_first",
                    prompt = "first",
                    itemIndex = 0,
                ),
                StudioPromptHeaderInfo(
                    key = "studio_header_1_second",
                    prompt = "second",
                    itemIndex = 2,
                ),
            ),
            headers,
        )
    }

    @Test
    fun promptHeaders_whenGeneratingPromptCompletes_keepsSameHeaderKey() {
        val generatingHeaders = buildStudioPromptHeaderInfo(
            groupedGallery = listOf(
                StudioGalleryGroup(
                    prompt = "first",
                    items = listOf(studioMedia("asset-1")),
                ),
            ),
            isGenerating = true,
            activeGenerationPrompt = "second",
            generationPlaceholderCount = 2,
        )
        val completedHeaders = buildStudioPromptHeaderInfo(
            groupedGallery = listOf(
                StudioGalleryGroup(
                    prompt = "first",
                    items = listOf(studioMedia("asset-1")),
                ),
                StudioGalleryGroup(
                    prompt = "second",
                    items = listOf(studioMedia("asset-2")),
                ),
            ),
            isGenerating = false,
            activeGenerationPrompt = null,
            generationPlaceholderCount = 2,
        )

        assertEquals(generatingHeaders.last().key, completedHeaders.last().key)
    }

    @Test
    fun stickyHeaderLayout_whenCurrentHeaderIsBelowAppBar_returnsNull() {
        val headers = listOf(StudioPromptHeaderInfo(key = "header-1", prompt = "first", itemIndex = 0))

        val result = calculateStudioStickyHeaderLayout(
            headers = headers,
            firstVisibleItemIndex = 0,
            visibleItems = listOf(
                StudioVisibleListItemInfo(key = "header-1", offset = 24),
            ),
            stickyTopPx = 96,
            headerHeightPx = 48,
        )

        assertEquals(null, result)
    }

    @Test
    fun stickyHeaderLayout_whenHeaderHasScrolledUnderAppBar_sticksAtAppBarBottom() {
        val headers = listOf(StudioPromptHeaderInfo(key = "header-1", prompt = "first", itemIndex = 0))

        val result = calculateStudioStickyHeaderLayout(
            headers = headers,
            firstVisibleItemIndex = 1,
            visibleItems = listOf(
                StudioVisibleListItemInfo(key = "row-1", offset = -20),
            ),
            stickyTopPx = 96,
            headerHeightPx = 48,
        )

        assertEquals(StudioStickyHeaderLayout(key = "header-1", prompt = "first", yOffset = 96), result)
    }

    @Test
    fun stickyHeaderLayout_whenNextHeaderHasGap_doesNotPushCurrentHeaderUp() {
        val headers = listOf(
            StudioPromptHeaderInfo(key = "header-1", prompt = "first", itemIndex = 0),
            StudioPromptHeaderInfo(key = "header-2", prompt = "second", itemIndex = 3),
        )

        val result = calculateStudioStickyHeaderLayout(
            headers = headers,
            firstVisibleItemIndex = 2,
            visibleItems = listOf(
                StudioVisibleListItemInfo(key = "row-2", offset = 80),
                StudioVisibleListItemInfo(key = "header-2", offset = 130),
            ),
            stickyTopPx = 96,
            headerHeightPx = 48,
        )

        assertEquals(StudioStickyHeaderLayout(key = "header-1", prompt = "first", yOffset = 96), result)
    }

    @Test
    fun stickyHeaderLayout_whenNextHeaderApproaches_pushesCurrentHeaderUp() {
        val headers = listOf(
            StudioPromptHeaderInfo(key = "header-1", prompt = "first", itemIndex = 0),
            StudioPromptHeaderInfo(key = "header-2", prompt = "second", itemIndex = 3),
        )

        val result = calculateStudioStickyHeaderLayout(
            headers = headers,
            firstVisibleItemIndex = 2,
            visibleItems = listOf(
                StudioVisibleListItemInfo(key = "row-2", offset = -20),
                StudioVisibleListItemInfo(key = "header-2", offset = 34),
            ),
            stickyTopPx = 96,
            headerHeightPx = 48,
        )

        assertEquals(StudioStickyHeaderLayout(key = "header-1", prompt = "first", yOffset = 82), result)
    }

    @Test
    fun expandedHeaderKeys_whenExpandingHeader_addsHeaderKey() {
        val result = updateStudioExpandedHeaderKeys(
            expandedHeaderKeys = listOf("header-1"),
            headerKey = "header-2",
            isExpanded = true,
        )

        assertEquals(listOf("header-1", "header-2"), result)
    }

    @Test
    fun expandedHeaderKeys_whenExpandingAlreadyExpandedHeader_doesNotDuplicateHeaderKey() {
        val result = updateStudioExpandedHeaderKeys(
            expandedHeaderKeys = listOf("header-1"),
            headerKey = "header-1",
            isExpanded = true,
        )

        assertEquals(listOf("header-1"), result)
    }

    @Test
    fun expandedHeaderKeys_whenCollapsingHeader_removesHeaderKey() {
        val result = updateStudioExpandedHeaderKeys(
            expandedHeaderKeys = listOf("header-1", "header-2"),
            headerKey = "header-1",
            isExpanded = false,
        )

        assertEquals(listOf("header-2"), result)
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
