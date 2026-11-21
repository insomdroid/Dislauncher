package com.insomdroid.dislauncher

internal fun buildLocalNoteSnapshot(
    id: String,
    title: String,
    markdown: String,
    existing: LocalNote?,
    labels: List<String>,
    color: String,
    updatedAt: Long
): LocalNote {
    val document = NoteDocumentCodec.fromLegacyContent(markdown)
    return LocalNote(
        id = id,
        title = title,
        content = NoteDocumentCodec.plainText(document),
        updatedAt = updatedAt,
        pinned = existing?.pinned ?: false,
        documentJson = NoteDocumentCodec.encode(document),
        labels = labels,
        color = color,
        markdown = markdown
    )
}
