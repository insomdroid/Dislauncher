package com.insomdroid.dislauncher

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets

/** Disk-backed page list: only page offsets stay in memory. */
internal class DiskBackedBookPages private constructor(
    private val file: File,
    private val offsets: LongArray
) : AbstractList<String>() {
    override val size: Int get() = offsets.size
    private val indexFile: File get() = File(file.parentFile, file.name + ".idx")
    val isAvailable: Boolean get() = file.isFile && indexFile.isFile
    val metadataBytes: Int get() = (offsets.size * Long.SIZE_BYTES).coerceAtMost(Int.MAX_VALUE)

    fun touch() {
        val now = System.currentTimeMillis()
        if (file.isFile) file.setLastModified(now)
        if (indexFile.isFile) indexFile.setLastModified(now)
    }

    companion object {
        private const val INDEX_MAGIC = 0x44504231
        private const val MAX_INDEXED_PAGES = 1_000_000

        fun open(file: File): DiskBackedBookPages? {
            val indexFile = File(file.parentFile, file.name + ".idx")
            if (!file.isFile || !indexFile.isFile) return null
            return runCatching {
                DataInputStream(BufferedInputStream(FileInputStream(indexFile))).use { input ->
                    require(input.readInt() == INDEX_MAGIC) { "Invalid reader page index." }
                    val count = input.readInt()
                    require(count in 1..MAX_INDEXED_PAGES) { "Invalid reader page count." }
                    val offsets = LongArray(count) { input.readLong() }
                    require(input.read() == -1) { "Reader page index contains trailing data." }
                    require(offsets.first() == 0L)
                    require((1 until offsets.size).all { offsets[it] > offsets[it - 1] })
                    require(offsets.all { it >= 0L && it + Int.SIZE_BYTES <= file.length() })
                    DiskBackedBookPages(file, offsets)
                }
            }.getOrNull()
        }
    }

    override fun get(index: Int): String {
        if (index !in offsets.indices) throw IndexOutOfBoundsException("Page $index is outside the book")
        RandomAccessFile(file, "r").use { input ->
            input.seek(offsets[index])
            val length = input.readInt()
            require(length >= 0 && length.toLong() <= file.length() - offsets[index] - Int.SIZE_BYTES) {
                "Cached reader page is corrupted."
            }
            val bytes = ByteArray(length)
            input.readFully(bytes)
            return String(bytes, StandardCharsets.UTF_8)
        }
    }

    internal class Writer(file: File, private val pageLimit: Int = 2600) {
        private val target = file
        private val output: DataOutputStream
        private val offsets = ArrayList<Long>()
        private var filePosition = 0L
        private val page = StringBuilder(pageLimit + 32)
        private var finished = false

        init {
            require(pageLimit >= 32)
            target.parentFile?.mkdirs()
            target.delete()
            File(target.parentFile, target.name + ".idx").delete()
            File(target.parentFile, target.name + ".idx.tmp").delete()
            output = DataOutputStream(BufferedOutputStream(FileOutputStream(target)))
        }

        val pageCount: Int get() = offsets.size

        fun append(text: CharSequence, cancellationCheck: () -> Unit = {}) {
            check(!finished)
            text.forEachIndexed { index, character ->
                if (index % 1024 == 0) cancellationCheck()
                when {
                    character == '\r' -> Unit
                    character == '\n' -> {
                        while (page.isNotEmpty() && (page.last() == ' ' || page.last() == '\t')) {
                            page.deleteCharAt(page.lastIndex)
                        }
                        if (page.isEmpty()) {
                            page.append('\n')
                        } else if (page.last() != '\n') {
                            page.append('\n')
                        } else if (page.length < 2 || page[page.length - 2] != '\n') {
                            page.append('\n')
                        }
                    }
                    character.isWhitespace() -> {
                        if (page.isNotEmpty() && page.last() != ' ' && page.last() != '\n') {
                            page.append(' ')
                        }
                    }
                    else -> page.append(character)
                }
                if (page.length >= pageLimit) flushPage(false)
            }
        }

        fun finishSection(addParagraphSeparator: Boolean = false) {
            check(!finished)
            if (addParagraphSeparator && page.isNotEmpty()) append("\n\n")
            flushPage(true)
        }

        fun finish(): List<String> {
            check(!finished)
            flushPage(true)
            if (offsets.isEmpty()) writePage("This book contains no readable text.")
            output.close()
            val indexFile = File(target.parentFile, target.name + ".idx")
            val temporaryIndex = File(target.parentFile, target.name + ".idx.tmp")
            temporaryIndex.delete()
            DataOutputStream(BufferedOutputStream(FileOutputStream(temporaryIndex))).use { index ->
                index.writeInt(INDEX_MAGIC)
                index.writeInt(offsets.size)
                offsets.forEach(index::writeLong)
            }
            indexFile.delete()
            if (!temporaryIndex.renameTo(indexFile)) {
                temporaryIndex.delete()
                throw java.io.IOException("Unable to finalize the reader page index.")
            }
            finished = true
            return DiskBackedBookPages(target, offsets.toLongArray())
        }

        fun abort() {
            if (!finished) runCatching { output.close() }
            finished = true
            target.delete()
            File(target.parentFile, target.name + ".idx").delete()
            File(target.parentFile, target.name + ".idx.tmp").delete()
        }

        private fun flushPage(force: Boolean) {
            if (page.isEmpty()) return
            val text = page.toString()
            val end = if (force || text.length < pageLimit) text.length else {
                val boundary = text.lastIndexOfAny(charArrayOf(' ', '.', '!', '?', '\n'))
                if (boundary > pageLimit / 2) boundary + 1 else pageLimit
            }.coerceAtMost(text.length)
            val chunk = text.substring(0, end).trim(' ', '\t', '\r')
            if (chunk.isNotEmpty()) writePage(chunk)
            page.delete(0, end)
            while (page.isNotEmpty() && (page.first() == ' ' || page.first() == '\t' || page.first() == '\r')) page.deleteCharAt(0)
        }

        private fun writePage(value: String) {
            val bytes = value.toByteArray(StandardCharsets.UTF_8)
            offsets += filePosition
            output.writeInt(bytes.size)
            output.write(bytes)
            filePosition += Int.SIZE_BYTES + bytes.size
        }
    }
}
