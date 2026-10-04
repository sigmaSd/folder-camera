package org.foldercamera

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
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
        context.grantUriPermission(context.packageName, android.net.Uri.parse(tree), android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION or android.content.Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
        runBlocking {
            TestDocumentsProvider.rootDisplayName = "FolderCamera"
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
        rule.onNodeWithText("Projects/Studio/Before").assertExists()
        rule.onNodeWithText("Projects").performScrollTo()
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
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        File(folder, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val exported = "/sdcard/Download/folder-camera-store-screenshots"
        // UTP uninstalls the test app afterward; preserve emulator-only fixture screenshots in public test storage.
        fun shell(command: String): String = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).use { descriptor -> java.io.FileInputStream(descriptor.fileDescriptor).use { String(it.readBytes()) } }
        shell("mkdir -p $exported")
        shell("cp ${File(folder, name).absolutePath} $exported/$name")
        org.junit.Assert.assertTrue("Screenshot export failed", shell("ls $exported/$name").contains(name))
    }
}
