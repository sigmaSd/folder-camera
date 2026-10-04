package org.foldercamera

import org.foldercamera.core.FolderPaths
import org.junit.Assert.*
import org.junit.Test

class FolderPathsTest {
    @Test fun completeCatalogKeepsEveryPathAndItsAncestorsBeyondTwentyItems() {
        val folders = (1..1200).map { "Projects/Job $it/Before" }
        val all = FolderPaths.all(folders)
        assertEquals(2401, all.size)
        assertTrue("Projects/Job 1200/Before" in all)
        assertEquals(listOf("Projects"), FolderPaths.visible(all, "", ""))
        assertEquals(1200, FolderPaths.visible(all, "Projects", "").size)
    }
    @Test fun searchIsRecursiveAndUnicodeSpellingIsPreserved() {
        val all = FolderPaths.all(listOf("Été/صور/قبل", "Projects/Job A/Before", "Projects/Job B/After"))
        assertEquals(listOf("Été/صور/قبل"), FolderPaths.visible(all, "Projects", "قبل"))
        assertEquals(listOf("Projects/Job A/Before"), FolderPaths.visible(all, "", "before"))
        assertTrue("Été" in all); assertTrue("Été/صور" in all)
        assertEquals(listOf("Projects/Job A", "Projects/Job B"), FolderPaths.visible(all, "Projects", ""))
    }
}
