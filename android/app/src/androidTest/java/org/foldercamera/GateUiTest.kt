package org.foldercamera

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class GateUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    @Test fun freshLaunchIsDestinationGate() {
        rule.onNodeWithText("Start camera").assertExists()
        rule.onNodeWithContentDescription("Take photo").assertDoesNotExist()
        rule.onNodeWithText("Relative folder path").performTextInput("../unsafe")
        rule.onNodeWithText("Settings").performClick()
        rule.onNodeWithText("Optional LAN upload").assertExists()
        rule.onAllNodesWithText("Paths").onLast().performClick()
        rule.onNodeWithText("Start camera").assertExists()
    }
    @Test fun confirmingAndCancelingDestinationChangesPreservesSessionAcrossRotation() {
        rule.activityRule.scenario.onActivity { activity ->
            androidx.lifecycle.ViewModelProvider(activity)[org.foldercamera.ui.CameraModel::class.java].selectRoot(
                android.provider.DocumentsContract.buildTreeDocumentUri(TestDocumentsProvider.AUTHORITY, "root").toString())
        }
        rule.onNodeWithText("Relative folder path").performTextClearance()
        rule.onNodeWithText("Relative folder path").performTextInput("Projects/Job A/Before")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        rule.onNodeWithText("Start camera").performClick()
        rule.onNodeWithText("Projects/Job A/Before").assertExists()
        rule.onNodeWithContentDescription("Change folder").performClick()
        rule.onNodeWithText("Relative folder path").performTextClearance()
        rule.onNodeWithText("Relative folder path").performTextInput("New/صور")
        rule.onNodeWithText("Cancel — keep current destination").performClick()
        rule.onNodeWithText("Projects/Job A/Before").assertExists()
        rule.onNodeWithContentDescription("Change folder").performClick()
        rule.onNodeWithText("Relative folder path").performTextClearance()
        rule.onNodeWithText("Relative folder path").performTextInput("New/صور")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        rule.onNodeWithText("Start camera").performClick()
        rule.onNodeWithText("New/صور").assertExists()
        rule.activityRule.scenario.recreate()
        rule.onNodeWithText("New/صور").assertExists()
    }
}
