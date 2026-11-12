package org.foldercamera

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.foldercamera.data.Receiver
import org.foldercamera.ui.CameraModel
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class UiImprovementsTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val tree = DocumentsContract.buildTreeDocumentUri(TestDocumentsProvider.AUTHORITY, "root").toString()
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as FolderCameraApp
    @Before fun prepare() {
        app.config.syncEnabled = false
        TestDocumentsProvider.denyAccess = false; TestDocumentsProvider.denyReads = false; TestDocumentsProvider.denyWrites = false
        app.grantUriPermission(app.packageName, Uri.parse(tree), Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
        runBlocking { app.db.clearAllTables() }
        File(app.filesDir, "test-documents").deleteRecursively()
    }
    @Test fun pathsHaveTheirOwnViewportAndSelectionDoesNotHideTheCameraAction() {
        val root = File(app.filesDir, "test-documents")
        (1..70).forEach { File(root, "Project %02d".format(it)).mkdirs() }
        rule.activityRule.scenario.onActivity { activity -> ViewModelProvider(activity)[CameraModel::class.java].selectRoot(tree) }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Project 01").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Project 01").assertIsDisplayed()
        rule.onNodeWithText("Start camera").assertIsDisplayed()
        rule.onNodeWithTag("path-list").performScrollToNode(hasText("Project 70"))
        rule.onNodeWithText("Project 70").performClick()
        rule.onAllNodesWithText("Project 70").onFirst().assertIsDisplayed()
        rule.onNodeWithText("Relative folder path").assertTextContains("Project 70")
        rule.onNodeWithText("Start camera").assertIsDisplayed()
        rule.onNodeWithText("Cancel — keep current destination").assertDoesNotExist()
    }
    @Test fun searchingAndOpeningAPhotoAtTheBottomAlwaysShowsAViewer() {
        val directory = File(app.filesDir, "test-documents").apply { mkdirs() }
        (1..30).forEach { index ->
            InstrumentationRegistry.getInstrumentation().context.assets.open("photo.jpg").use { input -> File(directory, "Photo %02d.jpg".format(index)).outputStream().use { input.copyTo(it) } }
        }
        rule.activityRule.scenario.onActivity { activity ->
            ViewModelProvider(activity)[CameraModel::class.java].apply { selectRoot(tree); screen = "browser" }
        }
        rule.onNodeWithText("Search this folder").performTextInput("Photo 24")
        dismissKeyboard()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Photo 24.jpg").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Photo 01.jpg").assertDoesNotExist()
        rule.onNodeWithText("Photo 24.jpg").performClick()
        rule.onNodeWithContentDescription("Close photo").assertIsDisplayed().performClick()
        rule.onNodeWithText("Search this folder").assertTextContains("Photo 24")
        rule.onNodeWithContentDescription("Clear search").performClick()
        rule.onNodeWithTag("browser-list").performScrollToNode(hasText("Photo 30.jpg"))
        rule.onNodeWithText("Photo 30.jpg").performClick()
        rule.onNodeWithContentDescription("Close photo").assertIsDisplayed()
        // The viewer preloads the next photo off screen; check the one at the left edge (the photo being shown).
        val onScreen = hasContentDescription("Photo preview") and SemanticsMatcher("starts at the left edge") { kotlin.math.abs(it.boundsInRoot.left) < 1f }
        rule.waitUntil(10_000) { rule.onAllNodes(onScreen).fetchSemanticsNodes().isNotEmpty() }
        rule.onNode(onScreen).assertIsDisplayed()
        rule.onNodeWithContentDescription("Close photo").performClick()
        rule.onNodeWithText("Photo 30.jpg").assertIsDisplayed()
    }
    @Test fun photosAreNewestFirstAndTheViewerSwipesBetweenThem() {
        val directory = File(app.filesDir, "test-documents").apply { mkdirs() }
        File(directory, "Album").mkdirs()
        listOf("20260101_090000_000_a.jpg", "20260102_090000_000_b.jpg", "20260103_090000_000_c.jpg").forEach { name ->
            InstrumentationRegistry.getInstrumentation().context.assets.open("photo.jpg").use { input -> File(directory, name).outputStream().use { input.copyTo(it) } }
        }
        rule.activityRule.scenario.onActivity { activity ->
            ViewModelProvider(activity)[CameraModel::class.java].apply { selectRoot(tree); screen = "browser" }
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("20260101_090000_000_a.jpg").fetchSemanticsNodes().isNotEmpty() }
        fun top(name: String) = rule.onNodeWithText(name).fetchSemanticsNode().boundsInRoot.top
        org.junit.Assert.assertTrue("Folders stay above files", top("Album") < top("20260103_090000_000_c.jpg"))
        org.junit.Assert.assertTrue("Newest photo first", top("20260103_090000_000_c.jpg") < top("20260102_090000_000_b.jpg"))
        org.junit.Assert.assertTrue("Oldest photo last", top("20260102_090000_000_b.jpg") < top("20260101_090000_000_a.jpg"))
        rule.onNodeWithText("20260103_090000_000_c.jpg").performClick()
        rule.onNodeWithText("1 / 3").assertIsDisplayed()
        rule.onNodeWithText("Open with").assertIsDisplayed()
        rule.onNodeWithTag("photo-pager").performTouchInput { swipeLeft() }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("2 / 3").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("photo-pager").performTouchInput { swipeLeft() }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("3 / 3").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("photo-pager").performTouchInput { swipeRight() }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("2 / 3").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("Close photo").performClick()
        rule.onNodeWithText("2 / 3").assertDoesNotExist()
    }
    @Test fun qrPairingADifferentPcOffersReplacementWithoutResettingTheApp() {
        val old = Receiver("old-pc", "https://192.168.1.2:8443", "a".repeat(64), "old-token")
        val next = Receiver("new-pc", "https://192.168.1.3:8443", "b".repeat(64), "new-token")
        app.config.setReceiver(old)
        lateinit var model: CameraModel
        rule.activityRule.scenario.onActivity { activity ->
            model = ViewModelProvider(activity)[CameraModel::class.java]
            model.receiver = old; model.sync = false; model.screen = "scanner"
        }
        // The QR and manual handlers feed their verified pair response into this same method.
        runBlocking { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { model.connectReceiver(next) } }
        rule.onNodeWithText("Replace active receiver?").assertIsDisplayed()
        org.junit.Assert.assertEquals(old, app.config.receiver())
        rule.onNodeWithText("Keep original receiver").performClick()
        org.junit.Assert.assertEquals(old, app.config.receiver())
        runBlocking { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { model.connectReceiver(next) } }
        rule.onNodeWithText("Discard old tasks and replace").performClick()
        rule.waitUntil(10_000) { model.receiver == next }
        org.junit.Assert.assertEquals(next, app.config.receiver())
        rule.onNodeWithText("Replace active receiver?").assertDoesNotExist()
    }
    private fun dismissKeyboard() = rule.runOnUiThread {
        val view = rule.activity.window.decorView
        rule.activity.getSystemService(android.view.inputmethod.InputMethodManager::class.java).hideSoftInputFromWindow(view.windowToken, 0)
        view.clearFocus()
    }
}
