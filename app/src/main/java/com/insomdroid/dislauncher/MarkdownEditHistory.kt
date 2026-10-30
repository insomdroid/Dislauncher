package com.insomdroid.dislauncher

internal const val MAX_MARKDOWN_HISTORY_STEPS = 50

internal data class MarkdownHistoryTransition(
    val history: MarkdownEditHistory,
    val text: String
)

internal data class MarkdownEditHistory(
    val undo: List<String> = emptyList(),
    val redo: List<String> = emptyList()
) {
    fun record(currentText: String) =
        copy(undo = (undo + currentText).takeLast(MAX_MARKDOWN_HISTORY_STEPS), redo = emptyList())

    fun undo(currentText: String): MarkdownHistoryTransition? {
        val previous = undo.lastOrNull() ?: return null
        return MarkdownHistoryTransition(
            copy(
                undo = undo.dropLast(1),
                redo = (redo + currentText).takeLast(MAX_MARKDOWN_HISTORY_STEPS)
            ),
            previous
        )
    }

    fun redo(currentText: String): MarkdownHistoryTransition? {
        val next = redo.lastOrNull() ?: return null
        return MarkdownHistoryTransition(
            copy(
                undo = (undo + currentText).takeLast(MAX_MARKDOWN_HISTORY_STEPS),
                redo = redo.dropLast(1)
            ),
            next
        )
    }
}
