package com.browntowndev.pocketcrew.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.browntowndev.pocketcrew.presentation.MainActivity
import com.browntowndev.pocketcrew.core.ui.error.ViewModelErrorHandler
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class MainScreenErrorTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var errorHandler: ViewModelErrorHandler

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun snackbarIsDisplayedOnErrorEvent() = runTest {
        val errorMessage = "Test error message"
        
        // When
        errorHandler.handleError("TestTag", "Dev message", RuntimeException(), errorMessage)
        
        // Then
        composeTestRule.onNodeWithText(errorMessage).assertIsDisplayed()
    }
}
