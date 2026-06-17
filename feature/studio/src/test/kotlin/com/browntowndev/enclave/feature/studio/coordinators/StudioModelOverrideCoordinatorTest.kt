package com.browntowndev.enclave.feature.studio.coordinators

import app.cash.turbine.test
import com.browntowndev.enclave.domain.model.config.MediaProviderId
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.media.StudioModelOverride
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class StudioModelOverrideCoordinatorTest {
    private lateinit var coordinator: StudioModelOverrideCoordinator

    @BeforeEach
    fun setup() {
        coordinator = StudioModelOverrideCoordinator()
    }

    @Nested
    @DisplayName("S08 — Selecting a row marks it primary; Confirm commits")
    inner class S08StageAndConfirm {
        @Test
        @DisplayName("S08 — stageSelection then confirmSelection commits override and hides picker")
        fun s08_stageAndConfirm() = runTest {
            val override = StudioModelOverride.MediaProvider(MediaProviderId("fal-2"), "Fal Flux")

            coordinator.openPicker(ModelType.IMAGE_GENERATION, null)
            coordinator.stageSelection(override)

            coordinator.state.map { it.committedOverride }.test {
                assertNull(awaitItem())
                coordinator.confirmSelection()
                assertEquals(override, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            assertFalse(coordinator.state.value.picker.isVisible)
        }
    }

    @Nested
    @DisplayName("S09 — Back/Cancel discards staged selection and returns to Settings view")
    inner class S09Cancel {
        @Test
        @DisplayName("S09 — cancelPicker clears pending, hides picker, keeps committed override null")
        fun s09_cancelPicker() = runTest {
            val override = StudioModelOverride.MediaProvider(MediaProviderId("fal-2"), "Fal Flux")

            coordinator.openPicker(ModelType.IMAGE_GENERATION, null)
            coordinator.stageSelection(override)

            coordinator.cancelPicker()

            assertNull(coordinator.state.value.committedOverride)
            assertNull(coordinator.state.value.picker.pendingSelection)
            assertFalse(coordinator.state.value.picker.isVisible)
        }
    }

    @Nested
    @DisplayName("E01 — Confirm disabled when no row staged")
    inner class E01ConfirmDisabled {
        @Test
        @DisplayName("E01 — confirmSelection with null pending is a no-op (override unchanged)")
        fun e01_confirmWithNullPending() = runTest {
            coordinator.openPicker(ModelType.IMAGE_GENERATION, null)

            coordinator.confirmSelection()

            assertNull(coordinator.state.value.committedOverride)
        }
    }

    @Nested
    @DisplayName("E02 — Confirm re-confirmation is idempotent")
    inner class E02IdempotentConfirm {
        @Test
        @DisplayName("E02 — Re-confirm emits no new distinct override value")
        fun e02_idempotentConfirm() = runTest {
            val override = StudioModelOverride.MediaProvider(MediaProviderId("fal-1"), "Fal Aura")

            coordinator.openPicker(ModelType.IMAGE_GENERATION, null)
            coordinator.stageSelection(override)
            coordinator.confirmSelection()

            coordinator.state.map { it.committedOverride }.test {
                assertEquals(override, awaitItem())
                coordinator.confirmSelection()
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
            assertFalse(coordinator.state.value.picker.isVisible)
        }
    }

    @Nested
    @DisplayName("E03 — Opening picker twice resets pending to current committed override")
    inner class E03OpenPickerTwice {
        @Test
        @DisplayName("E03 — Second openPicker re-seeds pending from committed override")
        fun e03_openPickerTwice() = runTest {
            val override = StudioModelOverride.MediaProvider(MediaProviderId("fal-1"), "Fal Aura")

            coordinator.openPicker(ModelType.IMAGE_GENERATION, null)
            coordinator.stageSelection(override)
            coordinator.confirmSelection()
            coordinator.openPicker(ModelType.IMAGE_GENERATION, null)
            coordinator.cancelPicker()

            coordinator.openPicker(ModelType.IMAGE_GENERATION, coordinator.state.value.committedOverride)

            assertEquals(override, coordinator.state.value.picker.pendingSelection)
        }
    }

    @Nested
    @DisplayName("E04 — Staging a different row replaces the prior staged selection")
    inner class E04StageReplaces {
        @Test
        @DisplayName("E04 — Staging fal-2 after fal-1 replaces pending")
        fun e04_stageReplaces() = runTest {
            val override1 = StudioModelOverride.MediaProvider(MediaProviderId("fal-1"), "Fal Aura")
            val override2 = StudioModelOverride.MediaProvider(MediaProviderId("fal-2"), "Fal Flux")

            coordinator.openPicker(ModelType.IMAGE_GENERATION, null)
            coordinator.stageSelection(override1)
            coordinator.stageSelection(override2)

            assertEquals(override2, coordinator.state.value.picker.pendingSelection)
        }
    }
}