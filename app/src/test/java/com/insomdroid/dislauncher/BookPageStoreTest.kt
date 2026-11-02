package com.insomdroid.dislauncher

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookPageStoreTest {
    private fun temporaryFile(): File {
        val directory = Files.createTempDirectory("dislauncher-book-pages-test").toFile()
        directory.deleteOnExit()
        return File(directory, "book.pages")
    }

    @Test
    fun writesAndReopensPagesFromDisk() {
        val file = temporaryFile()
        val writer = DiskBackedBookPages.Writer(file, pageLimit = 32)
        writer.append("First paragraph with enough words to create pages.\n\nSecond paragraph.")
        val written = writer.finish() as DiskBackedBookPages

        assertEquals(written.toList(), DiskBackedBookPages.open(file)!!.toList())
        assertTrue(written.isAvailable)
    }

    @Test
    fun createsReadablePlaceholderForEmptyBooks() {
        val file = temporaryFile()
        val pages = DiskBackedBookPages.Writer(file).finish()

        assertEquals(1, pages.size)
        assertEquals("This book contains no readable text.", pages[0])
    }

    @Test
    fun rejectsMissingAndCorruptedIndexes() {
        val file = temporaryFile()
        DiskBackedBookPages.Writer(file).apply { append("A readable page.") }.finish()
        assertTrue(DiskBackedBookPages.open(file) != null)

        File(file.parentFile, file.name + ".idx").writeBytes(byteArrayOf(0, 0, 0, 0))
        assertNull(DiskBackedBookPages.open(file))
    }

    @Test
    fun abortRemovesPartialCacheFiles() {
        val file = temporaryFile()
        val writer = DiskBackedBookPages.Writer(file)
        writer.append("Partial content")
        writer.abort()

        assertFalse(file.exists())
        assertFalse(File(file.parentFile, file.name + ".idx").exists())
        assertNull(DiskBackedBookPages.open(file))
    }

    @Test
    fun rejectsOutOfRangePageIndexes() {
        val pages = DiskBackedBookPages.Writer(temporaryFile()).apply {
            append("Only one page.")
        }.finish()

        try {
            pages[pages.size]
            throw AssertionError("Expected an out-of-range page access to fail")
        } catch (_: IndexOutOfBoundsException) {
            // Expected.
        }
    }
}
