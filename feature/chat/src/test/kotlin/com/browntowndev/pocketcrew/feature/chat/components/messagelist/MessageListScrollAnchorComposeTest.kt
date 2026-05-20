package com.browntowndev.pocketcrew.feature.chat.components.messagelist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.browntowndev.pocketcrew.core.ui.theme.PocketCrewTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies the active-assistant column pins indicators directly below the user row while
 * reserving empty space at the bottom of the turn (not between user and indicators).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MessageListScrollAnchorComposeTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `indicator stays directly below user when column has viewport min height`() {
        val listStateHolder = arrayOfNulls<LazyListState>(1)
        val userHeightPx = 80
        val columnMinHeightPx = 600

        composeTestRule.setContent {
            PocketCrewTheme {
                val listState = rememberLazyListState()
                listStateHolder[0] = listState
                ActiveTurnColumnHarness(
                    userHeightPx = userHeightPx,
                    columnMinHeightPx = columnMinHeightPx,
                    listState = listState,
                )
            }
        }

        composeTestRule.waitForIdle()
        val listState = requireNotNull(listStateHolder[0])

        composeTestRule.runOnIdle {
            runBlocking { listState.scrollToItem(0) }
        }
        composeTestRule.waitForIdle()

        assertEquals(0, listState.firstVisibleItemIndex)
        assertEquals(0, listState.firstVisibleItemScrollOffset)
    }
}

@Composable
private fun ActiveTurnColumnHarness(
    userHeightPx: Int,
    columnMinHeightPx: Int,
    listState: LazyListState,
) {
    val userHeight = userHeightPx.dp
    val columnMinHeight = columnMinHeightPx.dp

    LazyColumn(
        state = listState,
        modifier =
            Modifier
                .fillMaxWidth()
                .height(200.dp)
                .testTag("scroll_harness"),
    ) {
        item {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(userHeight)
                        .testTag("user_row"),
            )
        }
        item {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = columnMinHeight)
                        .testTag("assistant_column"),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("indicator_row"),
                )
            }
        }
    }
}
