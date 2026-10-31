package com.insomdroid.dislauncher

import java.nio.charset.StandardCharsets

internal const val MAX_MARKDOWN_HISTORY_STEPS = 50
internal const val MAX_MARKDOWN_HISTORY_BYTES = 2 * 1024 * 1024
private const val MARKDOWN_HISTORY_COALESCE_MS = 700L

internal data class MarkdownHistoryEntry(
    val text: String,
    val cursor: Int,
    val recordedAtMillis: Long
) {
    val byteCount: Int get() = text.toByteArray(StandardCharsets.UTF_8).size
}

internal data class MarkdownHistoryTransition(
    val history: MarkdownEditHistory,
    val text: String,
    val cursor: Int
)

internal data class MarkdownEditHistory(
    val undo: List<MarkdownHistoryEntry> = emptyList(),
    val redo: List<MarkdownHistoryEntry> = emptyList(),
    private val lastRecordedAtMillis: Long = 0L
) {
    fun record(
        currentText: String,
        cursor: Int = currentText.length,
        nowMillis: Long = System.currentTimeMillis()
    ): MarkdownEditHistory {
        val entry = MarkdownHistoryEntry(
            text = currentText,
            cursor = cursor.coerceIn(0, currentText.length),
            recordedAtMillis = nowMillis
        )
        if (entry.byteCount > MAX_MARKDOWN_HISTORY_BYTES) {
            return copy(redo = emptyList(), lastRecordedAtMillis = nowMillis)
        }

        val coalesce = lastRecordedAtMillis > 0L &&
            nowMillis - lastRecordedAtMillis in 0..MARKDOWN_HISTORY_COALESCE_MS
        val nextUndo = if (coalesce) undo else trimHistory(undo + entry)
        return copy(
            undo = nextUndo,
            redo = emptyList(),
            lastRecordedAtMillis = nowMillis
        )
    }

    fun undo(
        currentText: String,
        cursor: Int = currentText.length
    ): MarkdownHistoryTransition? {
        val previous = undo.lastOrNull() ?: return null
        val current = MarkdownHistoryEntry(
            currentText,
            cursor.coerceIn(0, currentText.length),
            System.currentTimeMillis()
        )
        return MarkdownHistoryTransition(
            history = copy(
                undo = undo.dropLast(1),
                redo = trimHistory(redo + current),
                lastRecordedAtMillis = 0L
            ),
            text = previous.text,
            cursor = previous.cursor.coerceIn(0, previous.text.length)
        )
    }

    fun redo(
        currentText: String,
        cursor: Int = currentText.length
    ): MarkdownHistoryTransition? {
        val next = redo.lastOrNull() ?: return null
        val current = MarkdownHistoryEntry(
            currentText,
            cursor.coerceIn(0, currentText.length),
            System.currentTimeMillis()
        )
        return MarkdownHistoryTransition(
            history = copy(
                undo = trimHistory(undo + current),
                redo = redo.dropLast(1),
                lastRecordedAtMillis = 0L
            ),
            text = next.text,
            cursor = next.cursor.coerceIn(0, next.text.length)
        )
    }

    private fun trimHistory(entries: List<MarkdownHistoryEntry>): List<MarkdownHistoryEntry> {
        val boundedSteps = entries.takeLast(MAX_MARKDOWN_HISTORY_STEPS).toMutableList()
        var totalBytes = boundedSteps.sumOf { it.byteCount.toLong() }
        while (boundedSteps.isNotEmpty() && totalBytes > MAX_MARKDOWN_HISTORY_BYTES) {
            totalBytes -= boundedSteps.removeAt(0).byteCount
        }
        return boundedSteps
    }
}
