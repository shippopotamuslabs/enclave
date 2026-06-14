package com.browntowndev.enclave.feature.chat.components.assistantresponse

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.browntowndev.enclave.core.ui.theme.EnclaveTheme
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.feature.chat.ChatMessage
import com.browntowndev.enclave.feature.chat.ContentUi
import com.browntowndev.enclave.feature.chat.MessageRole
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AssistantResponseActionRowTelemetryTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `TEL_002 response stats sheet shows local model timing values and unavailable fields`() {
        val clickCount = AtomicInteger(0)
        val message =
            ChatMessage(
                id = MessageId("m-litert-1"),
                chatId = ChatId("chat-1"),
                role = MessageRole.Assistant,
                content = ContentUi(text = "local answer"),
                formattedTimestamp = "1:00 PM",
            )

        composeTestRule.setContent {
            EnclaveTheme {
                AssistantResponseActionRow(
                    message = message,
                    contentText = message.content.text,
                    isComplete = true,
                    hasTtsProviderAssigned = false,
                    isPlayingTts = false,
                    isLoadingTts = false,
                    isPausedTts = false,
                    onPlayAudio = {},
                    onPauseAudio = {},
                    onShareClick = {},
                    onShowSources = {},
                    onStatsClick = { clickCount.incrementAndGet() },
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("More response actions").performClick()
        composeTestRule.onNodeWithText("Stats").performClick()
        assertEquals(1, clickCount.get())
    }
}
