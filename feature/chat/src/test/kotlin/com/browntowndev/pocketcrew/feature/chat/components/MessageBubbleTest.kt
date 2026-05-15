package com.browntowndev.pocketcrew.feature.chat.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.browntowndev.pocketcrew.core.ui.theme.PocketCrewTheme
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.feature.chat.ChatMessage
import com.browntowndev.pocketcrew.feature.chat.ContentUi
import com.browntowndev.pocketcrew.feature.chat.MessageRole
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(instrumentedPackages = ["androidx.loader.content"])
class MessageBubbleTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `short plain message does not show expand chevron`() {
        composeTestRule.setContent {
            PocketCrewTheme {
                MessageBubble(
                    message =
                        ChatMessage(
                            id = MessageId("1"),
                            chatId = ChatId("c1"),
                            role = MessageRole.User,
                            content = ContentUi(text = "Hi"),
                            formattedTimestamp = "10:00 AM",
                        ),
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Expand message").assertDoesNotExist()
    }

    @Test
    fun `long plain message expand control toggles`() {
        val longBody = (1..15).joinToString("\n") { "Line $it ".repeat(12) }
        composeTestRule.setContent {
            PocketCrewTheme {
                MessageBubble(
                    message =
                        ChatMessage(
                            id = MessageId("2"),
                            chatId = ChatId("c1"),
                            role = MessageRole.User,
                            content = ContentUi(text = longBody),
                            formattedTimestamp = "10:01 AM",
                        ),
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Expand message").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Expand message").performClick()
        composeTestRule.onNodeWithContentDescription("Collapse message").assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription("Collapse message").performClick()
        composeTestRule.onNodeWithContentDescription("Expand message").assertIsDisplayed()
    }
}
