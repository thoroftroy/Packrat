package dev.wonderdesk.shared

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ArchiveIndexTest {
    @Test fun summaryAndCaseInsensitivePathFilter() {
        val items = listOf(ZipItem("Photos", 0, true), ZipItem("Photos/Cat.png", 20, false), ZipItem("Notes.txt", 5, false))
        assertEquals(ArchiveSummary(2, 1, 25), summarizeArchive(items))
        assertEquals(listOf(items[1]), filterArchive(items, " cat "))
        assertEquals(items, filterArchive(items, "  "))
        assertEquals(emptyList<ZipItem>(), filterArchive(items, "missing"))
    }
    @Test fun entriesSortByPathOrSizeWithFoldersFirst() {
        val dir = ZipItem("Photos", 0, true)
        val big = ZipItem("b.txt", 50, false)
        val small = ZipItem("a.txt", 3, false)
        assertEquals(listOf(dir, small, big), sortArchiveEntries(listOf(big, dir, small), ArchiveOrder.PATH))
        assertEquals(listOf(dir, big, small), sortArchiveEntries(listOf(small, dir, big), ArchiveOrder.SIZE))
    }
}
