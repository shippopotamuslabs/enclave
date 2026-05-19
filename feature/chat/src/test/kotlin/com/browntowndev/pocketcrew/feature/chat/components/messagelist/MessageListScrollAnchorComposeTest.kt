package com.browntowndev.pocketcrew.feature.chat.components.messagelist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.browntowndev.pocketcrew.core.ui.theme.PocketCrewTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies that viewport-filler shrink compensation keeps the first visible item offset stable
 * while assistant content grows (regression for streaming scroll jump).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MessageListScrollAnchorComposeTest {
  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun `filler shrink compensation preserves viewport scroll offset`() {
    var fillerHeightPx by mutableIntStateOf(400)
    val listStateHolder = arrayOfNulls<LazyListState>(1)

    composeTestRule.setContent {
      PocketCrewTheme {
        val state = rememberLazyListState()
        listStateHolder[0] = state
        FillerShrinkScrollHarness(
          fillerHeightPx = fillerHeightPx,
          listState = state,
        )
      }
    }

    composeTestRule.waitForIdle()
    val listState = requireNotNull(listStateHolder[0])

    composeTestRule.runOnIdle {
      runBlocking { listState.scrollToItem(2) }
    }
    composeTestRule.waitForIdle()

    val viewportBeforeShrink = listState.layoutInfo.viewportStartOffset

    composeTestRule.runOnIdle {
      fillerHeightPx = 200
    }
    composeTestRule.waitForIdle()

    assertEquals(viewportBeforeShrink, listState.layoutInfo.viewportStartOffset)
  }

  @Test
  fun `filler shrink without compensation changes viewport scroll offset`() {
    var fillerHeightPx by mutableIntStateOf(400)
    val listStateHolder = arrayOfNulls<LazyListState>(1)

    composeTestRule.setContent {
      PocketCrewTheme {
        val state = rememberLazyListState()
        listStateHolder[0] = state
        FillerShrinkScrollHarness(
          fillerHeightPx = fillerHeightPx,
          listState = state,
          applyCompensation = false,
        )
      }
    }

    composeTestRule.waitForIdle()
    val listState = requireNotNull(listStateHolder[0])

    composeTestRule.runOnIdle {
      runBlocking { listState.scrollToItem(2) }
    }
    composeTestRule.waitForIdle()

    val viewportBeforeShrink = listState.layoutInfo.viewportStartOffset

    composeTestRule.runOnIdle {
      fillerHeightPx = 200
    }
    composeTestRule.waitForIdle()

    assertNotEquals(viewportBeforeShrink, listState.layoutInfo.viewportStartOffset)
  }
}

@Composable
private fun FillerShrinkScrollHarness(
  fillerHeightPx: Int,
  listState: LazyListState,
  applyCompensation: Boolean = true,
) {
  var previousFillerHeightPx by mutableIntStateOf(-1)
  val fillerHeight = with(LocalDensity.current) { fillerHeightPx.toDp() }

  LaunchedEffect(fillerHeightPx, applyCompensation) {
    if (applyCompensation) {
      val deltaPx =
        messageListFillerScrollCompensationPx(
          previousFillerHeightPx = previousFillerHeightPx,
          newFillerHeightPx = fillerHeightPx,
          isScrollInProgress = listState.isScrollInProgress,
          suppressScrollAnchor = false,
        )
      if (deltaPx != null) {
        listState.scroll {
          scrollBy(deltaPx.toFloat())
        }
      }
    }
    previousFillerHeightPx = fillerHeightPx
  }

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
                .height(40.dp)
                .testTag("anchor_item"),
      )
    }
    item {
      Spacer(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(fillerHeight)
                .testTag("filler_item"),
      )
    }
    item {
      Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(80.dp)
                .testTag("tail_item"),
      )
    }
  }
}
