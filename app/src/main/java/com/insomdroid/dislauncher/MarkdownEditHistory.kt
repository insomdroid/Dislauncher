package com.insomdroid.dislauncher

import java.nio.charset.StandardCharsets

internal const val MAX_MARKDOWN_HISTORY_STEPS = 50
internal const val MAX_MARKDOWN_HISTORY_BYTES = 2 * 1024 * 1024
private const val MARKDOWN_HISTORY_COALESCE_MS = 700L

internal data class MarkdownHistoryEntry(
    val text: String,
    val cursor: Int,
    val recordedAtMillis: Long,
    val byteCount: Int = text.toByteArray(StandardCharsets.UTF_8).size
) {

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
            nowMillis - lastRecordedAtMillis in 0L..MARKDOWN_HISTORY_COALESCE_MS
        val nextUndo = if (coalesce) undo else undo + entry
        val (boundedUndo, boundedRedo) = trimStacks(nextUndo, emptyList())
        return copy(
            undo = boundedUndo,
            redo = boundedRedo,
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
        val (boundedUndo, boundedRedo) = trimStacks(undo.dropLast(1), redo + current)
        return MarkdownHistoryTransition(
            history = copy(
                undo = boundedUndo,
                redo = boundedRedo,
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
        val (boundedUndo, boundedRedo) = trimStacks(undo + current, redo.dropLast(1))
        return MarkdownHistoryTransition(
            history = copy(
                undo = boundedUndo,
                redo = boundedRedo,
                lastRecordedAtMillis = 0L
            ),
            text = next.text,
            cursor = next.cursor.coerceIn(0, next.text.length)
        )
    }

    private fun trimStacks(
        undoEntries: List<MarkdownHistoryEntry>,
        redoEntries: List<MarkdownHistoryEntry>
    ): Pair<List<MarkdownHistoryEntry>, List<MarkdownHistoryEntry>> {
        val boundedUndo = undoEntries.takeLast(MAX_MARKDOWN_HISTORY_STEPS).toMutableList()
        val boundedRedo = redoEntries.takeLast(MAX_MARKDOWN_HISTORY_STEPS).toMutableList()
        fun totalBytes() = boundedUndo.sumOf { it.byteCount.toLong() } +
            boundedRedo.sumOf { it.byteCount.toLong() }

        while (
            boundedUndo.size + boundedRedo.size > MAX_MARKDOWN_HISTORY_STEPS ||
            totalBytes() > MAX_MARKDOWN_HISTORY_BYTES
        ) {
            if (boundedUndo.isNotEmpty()) {
                boundedUndo.removeAt(0)
            } else if (boundedRedo.isNotEmpty()) {
                boundedRedo.removeAt(0)
            } else {
                break
            }
        }
        return boundedUndo to boundedRedo
    }
}
