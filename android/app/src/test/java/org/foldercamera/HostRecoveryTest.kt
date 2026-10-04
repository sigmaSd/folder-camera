package org.foldercamera

import android.content.Context
import android.provider.DocumentsContract
import androidx.room.Room
import kotlinx.coroutines.*
import org.foldercamera.data.*
import org.foldercamera.storage.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test
import java.io.File

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34], application = android.app.Application::class, shadows = [ModernProviderQuery::class])
class HostRecoveryTest {
    private lateinit var context: Context
    private lateinit var db: CameraDatabase
    private val tree = DocumentsContract.buildTreeDocumentUri(TestDocumentsProvider.AUTHORITY, "root").toString()
    @Before fun setup() {
        val base = org.robolectric.RuntimeEnvironment.getApplication() as android.app.Application
        // Robolectric does not implement persisted tree-prefix URI grants; emulate only this test authority.
        context = object : android.content.ContextWrapper(base) {
            override fun checkCallingOrSelfUriPermission(uri: android.net.Uri, modeFlags: Int): Int =
                if (uri.authority == TestDocumentsProvider.AUTHORITY && !TestDocumentsProvider.denyAccess) android.content.pm.PackageManager.PERMISSION_GRANTED else android.content.pm.PackageManager.PERMISSION_DENIED
        }
        val provider = TestDocumentsProvider()
        provider.attachInfo(context, android.content.pm.ProviderInfo().apply { authority = TestDocumentsProvider.AUTHORITY; exported = true; grantUriPermissions = true; readPermission = "android.permission.MANAGE_DOCUMENTS"; writePermission = "android.permission.MANAGE_DOCUMENTS" })
        org.robolectric.Shadows.shadowOf(base).grantPermissions("android.permission.MANAGE_DOCUMENTS")
        context.grantUriPermission(context.packageName, android.net.Uri.parse(tree), android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        org.robolectric.shadows.ShadowContentResolver.registerProviderInternal(TestDocumentsProvider.AUTHORITY, provider)
        TestDocumentsProvider.denyAccess = false; TestDocumentsProvider.denyWrites = false; TestDocumentsProvider.denyReads = false
        File(context.filesDir, "test-documents").deleteRecursively()
        val probe = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, android.net.Uri.parse(tree))!!
        assertTrue("provider uri=${probe.uri} type=${probe.type} name=${probe.name} permission=${context.checkCallingOrSelfUriPermission(probe.uri, 2)}", probe.canWrite())
        db = Room.inMemoryDatabaseBuilder(context, CameraDatabase::class.java).build()
    }
    @After fun teardown() { TestDocumentsProvider.denyAccess = false; TestDocumentsProvider.denyWrites = false; TestDocumentsProvider.denyReads = false; if (::db.isInitialized) db.close() }
    private suspend fun staged(path: String = "Été/صور/Job A", receiver: String? = null): Capture {
        val c = PhotoStore(context, db.dao()).begin(tree, path, receiver)
        javaClass.getResourceAsStream("/photo.jpg")!!.use { input -> File(c.stagingPath!!).outputStream().use { input.copyTo(it) } }
        return c
    }
    @Test fun revokedAccessAndOutOfSpaceRetainStagingThenRecoverExactTree() = runBlocking {
        val c = staged(); TestDocumentsProvider.denyAccess = true
        PhotoStore(context, db.dao()).persist(c.photoId)
        assertEquals("LOCAL_FAILED", db.dao().capture(c.photoId)!!.state); assertTrue(File(c.stagingPath!!).exists())
        TestDocumentsProvider.denyAccess = false; TestDocumentsProvider.denyWrites = true
        PhotoStore(context, db.dao()).reconcile(); assertEquals("LOCAL_FAILED", db.dao().capture(c.photoId)!!.state)
        TestDocumentsProvider.denyWrites = false; PhotoStore(context, db.dao()).reconcile()
        val saved = db.dao().capture(c.photoId)!!; assertEquals(saved.toString(), "SAVED", saved.state); assertFalse(File(c.stagingPath!!).exists())
        assertTrue(File(context.filesDir, "test-documents/${c.relativePath}/${c.filename}").exists())
    }
    @Test fun completedCopyBeforeStateCommitRecoversWithoutDuplicate() = runBlocking {
        val c = staged(receiver = "receiver-one"); TestDocumentsProvider.denyReads = true
        PhotoStore(context, db.dao()).persist(c.photoId)
        val failed = db.dao().capture(c.photoId)!!; assertEquals("LOCAL_FAILED", failed.state); assertNotNull(failed.toString(), failed.destinationDocumentUri)
        TestDocumentsProvider.denyReads = false; PhotoStore(context, db.dao()).reconcile()
        val saved = db.dao().capture(c.photoId)!!; assertEquals(saved.toString(), "SAVED", saved.state); assertEquals(failed.destinationDocumentUri, saved.destinationDocumentUri)
        assertEquals(1, File(context.filesDir, "test-documents/${c.relativePath}").listFiles()!!.size)
        assertEquals(1, db.dao().outstanding("receiver-one"))
        PhotoStore(context, db.dao()).reconcile(); assertEquals(1, db.dao().outstanding("receiver-one"))
    }
    @Test fun interruptedCaptureDoesNotClaimSavedAndSyncOffCreatesNoDelivery() = runBlocking {
        val store = PhotoStore(context, db.dao()); val interrupted = store.begin(tree, "A/B", null)
        File(interrupted.stagingPath!!).writeBytes(byteArrayOf(-1, -40, 1))
        PhotoStore(context, db.dao()).reconcile(); assertEquals("LOCAL_FAILED", db.dao().capture(interrupted.photoId)!!.state)
        assertTrue(File(interrupted.stagingPath!!).exists())
        val complete = staged(); store.persist(complete.photoId)
        assertEquals(db.dao().capture(complete.photoId).toString(), "SAVED", db.dao().capture(complete.photoId)!!.state); assertNull(db.dao().claim("receiver-one", Long.MAX_VALUE / 2))
    }
    @Test fun reopeningFolderNeverOverwritesAndNamesStayStable() = runBlocking {
        val first = staged(); val second = staged()
        assertNotEquals(first.photoId, second.photoId); assertNotEquals(first.filename, second.filename)
        val store = PhotoStore(context, db.dao()); store.persist(first.photoId); store.persist(second.photoId)
        assertEquals("SAVED", db.dao().capture(first.photoId)!!.state); assertEquals("SAVED", db.dao().capture(second.photoId)!!.state)
        assertEquals(2, File(context.filesDir, "test-documents/${first.relativePath}").listFiles()!!.size)
        store.reconcile(); assertEquals(first.filename, db.dao().capture(first.photoId)!!.filename)
        assertEquals(2, File(context.filesDir, "test-documents/${first.relativePath}").listFiles()!!.size)
    }
    @Test fun folderCatalogIncludesEmptyUnicodeFoldersWithoutRecencyLimit() = runBlocking {
        val root = File(context.filesDir, "test-documents")
        (1..35).forEach { File(root, "Project $it/Before").mkdirs() }
        File(root, "Été/صور/قبل").mkdirs()
        val (_, paths) = org.foldercamera.storage.FolderCatalog(context).scan(tree) { }
        assertEquals(73, paths.size)
        assertTrue("Project 35/Before" in paths); assertTrue("Été/صور/قبل" in paths)
        assertEquals(0, db.dao().saved().size)
    }
    @Test fun concurrentClaimsLeaseRecoveryAndOriginalReceiverBinding() = runBlocking {
        val c = staged(receiver = "old-receiver"); PhotoStore(context, db.dao()).persist(c.photoId)
        val claimed = coroutineScope { (1..8).map { async(Dispatchers.IO) { db.dao().claim("old-receiver", 1000) } }.awaitAll().filterNotNull() }
        assertEquals(1, claimed.size); assertNull(db.dao().claim("new-receiver", 1000)); assertNull(db.dao().claim("old-receiver", 2000))
        val retry = db.dao().claim("old-receiver", 602000)!!; assertEquals(c.photoId, retry.photoId)
        db.dao().finish(c.photoId, "old-receiver", claimed.single().claim!!, "RECEIVED", 0, null, "stale")
        assertNull(db.dao().claim("old-receiver", 602001))
        db.dao().finish(c.photoId, "old-receiver", retry.claim!!, "RECEIVED", 0, null, "valid")
        assertEquals(0, db.dao().outstanding("old-receiver"))
    }
}

// Robolectric's legacy resolver dispatch bypasses Android O's query-to-Bundle adapter.
@org.robolectric.annotation.Implements(android.provider.DocumentsProvider::class)
class ModernProviderQuery {
    @org.robolectric.annotation.RealObject lateinit var provider: android.provider.DocumentsProvider
    @org.robolectric.annotation.Implementation
    fun query(uri: android.net.Uri, projection: Array<String>?, selection: String?, args: Array<String>?, sortOrder: String?): android.database.Cursor? =
        provider.query(uri, projection, android.os.Bundle(), null)
}
