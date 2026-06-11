package com.browntowndev.enclave.feature.settings

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SwipeToDeleteBehaviorTest {
    // S2 — Last-preset guard disables swipe when exactly 1 config remains
    @Test
    fun `isPresetSwipeDeletionEnabled returns false when configuration count is 1`() {
        assertFalse(isPresetSwipeDeletionEnabled(configurationCount = 1))
    }

    // E1 — Swipe enabled for a config row when 2 or more configs exist
    @Test
    fun `isPresetSwipeDeletionEnabled returns true when configuration count is 2`() {
        assertTrue(isPresetSwipeDeletionEnabled(configurationCount = 2))
    }

    @Test
    fun `isPresetSwipeDeletionEnabled returns true when configuration count is greater than 2`() {
        assertTrue(isPresetSwipeDeletionEnabled(configurationCount = 5))
    }

    @Test
    fun `isPresetSwipeDeletionEnabled returns false when configuration count is 0`() {
        assertFalse(isPresetSwipeDeletionEnabled(configurationCount = 0))
    }

    // S4 — Removed-section asset is not swipeable (onRequestDeleteAsset == null)
    @Test
    fun `isLocalModelAssetSwipeable returns false when delete handler is null`() {
        assertFalse(isLocalModelAssetSwipeable(onRequestDeleteAsset = null))
    }

    // S3 — Models-section LocalModelAssetCard is swipeable when delete handler is provided
    @Test
    fun `isLocalModelAssetSwipeable returns true when delete handler is provided`() {
        val handler: (LocalModelAssetUi) -> Unit = { _ -> }
        assertTrue(isLocalModelAssetSwipeable(onRequestDeleteAsset = handler))
    }

    // E2 — Swipeability depends only on delete handler presence, not asset state
    @Test
    fun `isLocalModelAssetSwipeable returns true for soft-deleted asset with non-null handler`() {
        val handler: (LocalModelAssetUi) -> Unit = { _ -> }
        assertTrue(isLocalModelAssetSwipeable(onRequestDeleteAsset = handler))
    }
}
