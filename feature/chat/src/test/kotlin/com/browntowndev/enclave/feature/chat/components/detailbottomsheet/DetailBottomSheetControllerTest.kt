package com.browntowndev.enclave.feature.chat.components.detailbottomsheet

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DetailBottomSheetControllerTest {
    @Test
    fun `update replaces hosted content while preserving dismiss wiring`() {
        var hostedContent = ""
        var hostedElapsed = 0
        var dismissCount = 0

        val controller =
            DetailBottomSheetController(
                onShow = { config, elapsedSeconds ->
                    hostedContent = config.content
                    hostedElapsed = elapsedSeconds
                },
                onUpdate = { config, elapsedSeconds ->
                    hostedContent = config.content
                    if (elapsedSeconds != null) {
                        hostedElapsed = elapsedSeconds
                    }
                },
                onUpdateElapsed = { elapsedSeconds ->
                    hostedElapsed = elapsedSeconds
                },
                onDismiss = {
                    dismissCount += 1
                },
            )

        controller.show(
            DetailBottomSheetConfig.Thinking(
                isVisible = true,
                content = "first",
                durationSeconds = 0L,
                onDismiss = {},
                isStreaming = true,
            ),
            elapsedSeconds = 1,
        )
        controller.update(
            DetailBottomSheetConfig.Thinking(
                isVisible = true,
                content = "second chunk",
                durationSeconds = 0L,
                onDismiss = {},
                isStreaming = true,
            ),
            elapsedSeconds = 2,
        )

        assertEquals("second chunk", hostedContent)
        assertEquals(2, hostedElapsed)

        controller.dismiss()
        assertEquals(1, dismissCount)
    }
}
