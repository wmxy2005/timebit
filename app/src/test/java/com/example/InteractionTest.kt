package com.example

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.ui.TimeTrackApp
import com.example.ui.TimeTrackViewModel
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class InteractionTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun testAppInteractions() {
    val application = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = TimeTrackViewModel(application)

    composeTestRule.setContent {
      MyApplicationTheme {
        TimeTrackApp(viewModel)
      }
    }

    // --- Tab 1: Timer Screen Tests ---
    // Assert timer stopwatch container exists
    composeTestRule.onNodeWithTag("timer_stopwatch_container").assertExists()
    
    // Choose Category Study
    composeTestRule.onNodeWithTag("category_chip_study").performClick()
    
    // Enter task description
    composeTestRule.onNodeWithTag("timer_desc_input").performTextInput("正在研究深度学习算法")
    
    // Start the timer
    composeTestRule.onNodeWithTag("start_timer_button").performClick()
    
    // Verify running controls exist
    composeTestRule.onNodeWithTag("stop_timer_button").assertExists()
    composeTestRule.onNodeWithTag("cancel_timer_button").assertExists()
    
    // Cancel active timer
    composeTestRule.onNodeWithTag("cancel_timer_button").performClick()
    
    // App should go back to start state
    composeTestRule.onNodeWithTag("start_timer_button").assertExists()

    // --- Tab 2: Navigation to History Screen ---
    composeTestRule.onNodeWithTag("nav_tab_history").performClick()
    
    // --- Tab 3: Navigation to Stats Screen ---
    composeTestRule.onNodeWithTag("nav_tab_stats").performClick()
    
    // Verify top targets button exists and can open dialog
    composeTestRule.onNodeWithTag("open_targets_dialog").assertExists()
    
    // Navigate back to Timer Screen
    composeTestRule.onNodeWithTag("nav_tab_timer").performClick()

    // --- Tab 4: Manual Supplement Entry Dialog Tests ---
    // Open manual entry dialog
    composeTestRule.onNodeWithTag("open_manual_entry_dialog").performClick()
    
    // Check if dialog parent card is present
    composeTestRule.onNodeWithTag("manual_entry_parent_card").assertExists()
    
    // Try typing duration
    composeTestRule.onNodeWithTag("manual_duration_input").performTextClearance()
    composeTestRule.onNodeWithTag("manual_duration_input").performTextInput("45")
    
    // Select category Study ("学习") inside dialog
    composeTestRule.onNodeWithTag("dialog_category_chip_study").performClick()
    
    // Click Save inside dialog
    composeTestRule.onNodeWithTag("dialog_save_button").performClick()
    
    // Verify dialog dismissed (card no longer exists)
    composeTestRule.onNodeWithTag("manual_entry_parent_card").assertDoesNotExist()

    // Screenshot result
    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/interaction_verified.png")
  }
}
