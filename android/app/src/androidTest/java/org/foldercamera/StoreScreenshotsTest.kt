package org.foldercamera

import android.graphics.Bitmap
import android.os.Build
import android.provider.DocumentsContract
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.foldercamera.ui.CameraModel
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Native UI captures with generated fixtures only; never run against a physical camera/user photos. */
class StoreScreenshotsTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    @Test fun captureCleanStoreScreens() {
        org.junit.Assume.assumeTrue("Store screenshots are emulator-only", Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk", ignoreCase = true))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, "android.permission.CAMERA")
        val app = context.applicationContext as FolderCameraApp
        val tree = DocumentsContract.buildTreeDocumentUri(TestDocumentsProvider.AUTHORITY, "root").toString()
        runBlocking {
            app.db.clearAllTables()
            File(context.filesDir, "test-documents").deleteRecursively()
            TestDocumentsProvider.denyAccess = false; TestDocumentsProvider.denyWrites = false; TestDocumentsProvider.denyReads = false
            for ((path, fixture) in listOf("Projects/Studio/Before" to "demo_studio.jpg", "Projects/Studio/Before" to "demo_detail.jpg", "Travel/Weekend" to "demo_studio.jpg")) {
                val capture = app.store.begin(tree, path, null)
                instrumentation.context.assets.open(fixture).use { input -> File(capture.stagingPath!!).outputStream().use { input.copyTo(it) } }
                app.store.persist(capture.photoId)
            }
            app.config.syncEnabled = false
        }
        rule.activityRule.scenario.onActivity { activity ->
            val model = ViewModelProvider(activity)[CameraModel::class.java]
            model.selectRoot(tree); model.path = "Projects/Studio/Before"
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Projects").fetchSemanticsNodes().isNotEmpty() }
        screenshot("1-paths.png")
        rule.onNodeWithText("Start camera").performClick()
        rule.waitUntil(15_000) { rule.onAllNodesWithContentDescription("Take photo").fetchSemanticsNodes().any { !it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled) } }
        screenshot("2-camera.png")
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithText("Optional LAN upload").assertExists()
        screenshot("3-settings.png")
    }
    private fun screenshot(name: String) {
        rule.waitForIdle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(context.getExternalFilesDir(null), "store-screenshots").apply { mkdirs() }
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(folder, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
