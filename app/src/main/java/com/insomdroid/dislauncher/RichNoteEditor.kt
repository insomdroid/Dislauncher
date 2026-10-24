package com.insomdroid.dislauncher

import android.net.Uri
import android.widget.ImageView

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.FormatStrikethrough
import androidx.compose.material.icons.outlined.FormatUnderlined
import androidx.compose.material.icons.outlined.InsertLink
import androidx.compose.material.icons.outlined.HorizontalRule
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import java.util.UUID

/**
 * Block-based note editor. NoteDocument is the editing source of truth; formatting controls
 * mutate block types and inline marks rather than inserting visible markup characters.
 */
@Composable
internal fun RichNoteEditor(
    document: NoteDocument,
    onDocumentChange: (NoteDocument) -> Unit,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    onInsertImage: (() -> Unit)? = null
) {
    var activeBlockId by remember { mutableStateOf<String?>(null) }
    var activeField by remember { mutableStateOf(TextFieldValue("")) }
    var showLinkDialog by remember { mutableStateOf(false) }
    var linkUrl by remember { mutableStateOf("") }
    val linkColor = MaterialTheme.colorScheme.primary
    val fields = remember { mutableStateMapOf<String, TextFieldValue>() }

    fun updateBlock(blockId: String, transform: (NoteBlock) -> NoteBlock) {
        onDocumentChange(document.copy(blocks = document.blocks.map { if (it.id == blockId) transform(it) else it }))
    }
    fun applyMark(mark: NoteMark) {
        val id = activeBlockId ?: return
        val field = activeField
        val start = field.selection.min.coerceIn(0, field.text.length)
        val end = field.selection.max.coerceIn(start, field.text.length)
        if (start == end) return
        val style = when (mark) {
            NoteMark.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
            NoteMark.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
            NoteMark.STRIKETHROUGH -> SpanStyle(textDecoration = TextDecoration.LineThrough)
            NoteMark.CODE -> SpanStyle(fontFamily = FontFamily.Monospace)
            NoteMark.UNDERLINE -> SpanStyle(textDecoration = TextDecoration.Underline)
        }
        val styled = buildAnnotatedString {
            append(field.annotatedString)
            addStyle(style, start, end)
        }
        val next = field.copy(annotatedString = styled)
        activeField = next
        fields[id] = next
        updateBlock(id) { it.copy(spans = spansFromField(next)) }
    }
    fun changeActiveType(type: NoteBlockType) {
        val id = activeBlockId ?: document.blocks.lastOrNull()?.id ?: return
        updateBlock(id) { it.copy(type = type) }
    }
    fun changeIndent(delta: Int) {
        val id = activeBlockId ?: return
        updateBlock(id) { it.copy(indentLevel = (it.indentLevel + delta).coerceIn(0, 5)) }
    }
    fun moveActiveBlock(delta: Int) {
        val id = activeBlockId ?: return
        val from = document.blocks.indexOfFirst { it.id == id }
        if (from < 0) return
        val to = (from + delta).coerceIn(0, document.blocks.lastIndex)
        if (from == to) return
        val reordered = document.blocks.toMutableList()
        val block = reordered.removeAt(from)
        reordered.add(to, block)
        onDocumentChange(document.copy(blocks = reordered))
    }
    fun deleteActiveBlock() {
        val id = activeBlockId ?: return
        if (document.blocks.size <= 1) {
            updateBlock(id) { it.copy(type = NoteBlockType.PARAGRAPH, spans = emptyList(), checked = false, indentLevel = 0) }
            activeField = TextFieldValue("")
            fields[id] = activeField
            return
        }
        val index = document.blocks.indexOfFirst { it.id == id }
        val remaining = document.blocks.filterNot { it.id == id }
        onDocumentChange(document.copy(blocks = remaining))
        val next = remaining.getOrNull(index.coerceAtMost(remaining.lastIndex)) ?: remaining.last()
        activeBlockId = next.id
        activeField = fieldFromBlock(next, linkColor)
        fields[next.id] = activeField
    }
    fun applyLink(url: String) {
        val id = activeBlockId ?: return
        val field = activeField
        val start = field.selection.min.coerceIn(0, field.text.length)
        val end = field.selection.max.coerceIn(start, field.text.length)
        if (start == end || url.isBlank()) return
        val normalized = if (url.startsWith("https://") || url.startsWith("http://")) url else "https://$url"
        val linked = buildAnnotatedString {
            append(field.annotatedString)
            addStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline), start, end)
            addStringAnnotation("URL", normalized, start, end)
        }
        val next = field.copy(annotatedString = linked)
        activeField = next
        fields[id] = next
        updateBlock(id) { it.copy(spans = spansFromField(next)) }
        showLinkDialog = false
        linkUrl = ""
    }
    fun addBlock(type: NoteBlockType = NoteBlockType.PARAGRAPH) {
        val block = NoteBlock(id = UUID.randomUUID().toString(), type = type)
        onDocumentChange(document.copy(blocks = document.blocks + block))
        activeBlockId = block.id
        activeField = TextFieldValue("")
        fields[block.id] = activeField
    }

    Column(modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(document.blocks, key = { it.id }) { block ->
                val initial = remember(block.id) { fieldFromBlock(block, linkColor) }
                var field by remember(block.id) { mutableStateOf(initial) }
                LaunchedEffect(block.spans, block.type, block.checked) {
                    val fresh = fieldFromBlock(block, linkColor)
                    if (fresh.text != field.text || fresh.annotatedString.spanStyles != field.annotatedString.spanStyles) {
                        field = fresh
                        fields[block.id] = fresh
                        if (activeBlockId == block.id) activeField = fresh
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(start = (block.indentLevel * 16).dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    when (block.type) {
                        NoteBlockType.BULLET -> Text("•", Modifier.padding(end = 10.dp, top = 5.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        NoteBlockType.NUMBERED -> Text("${block.orderedIndex}.", Modifier.padding(end = 8.dp, top = 5.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        NoteBlockType.CHECKLIST -> Checkbox(
                            checked = block.checked,
                            onCheckedChange = { checked -> updateBlock(block.id) { it.copy(checked = checked) } },
                            enabled = !readOnly,
                            modifier = Modifier.size(36.dp)
                        )
                        NoteBlockType.QUOTE -> Text("▏", Modifier.padding(end = 8.dp), color = MaterialTheme.colorScheme.primary)
                        NoteBlockType.DIVIDER -> Unit
                        else -> Unit
                    }
                    if (block.type == NoteBlockType.DIVIDER) {
                        Surface(Modifier.weight(1f).height(1.dp).padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant) {}
                    } else if (block.spans.any { it.imageUri != null }) {
                        val imageUri = block.spans.firstNotNullOfOrNull { it.imageUri }
                        if (imageUri != null) {
                            AndroidView(
                                factory = { viewContext ->
                                    ImageView(viewContext).apply {
                                        adjustViewBounds = true
                                        scaleType = ImageView.ScaleType.FIT_CENTER
                                        setImageURI(Uri.parse(imageUri))
                                    }
                                },
                                update = { imageView -> imageView.setImageURI(Uri.parse(imageUri)) },
                                modifier = Modifier.weight(1f).fillMaxWidth().height(180.dp)
                            )
                        }
                    } else {
                        val textStyle = when (block.type) {
                            NoteBlockType.HEADING -> MaterialTheme.typography.titleLarge
                            NoteBlockType.CODE -> MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                            NoteBlockType.QUOTE -> MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic)
                            else -> MaterialTheme.typography.bodyLarge
                        }
                        BasicTextField(
                            value = field,
                            onValueChange = { next ->
                                if ('\n' in next.text && block.type != NoteBlockType.CODE) {
                                    val parts = next.text.split('\n')
                                    val replacementBlocks = parts.mapIndexed { index, part ->
                                        val start = parts.take(index).sumOf { it.length + 1 }
                                        val sliced = sliceField(next, start, start + part.length)
                                        val isTrailingEmptyListItem = part.isBlank() && index == parts.lastIndex &&
                                            block.type in setOf(NoteBlockType.BULLET, NoteBlockType.NUMBERED, NoteBlockType.CHECKLIST)
                                        val type = when {
                                            isTrailingEmptyListItem -> NoteBlockType.PARAGRAPH
                                            index > 0 && block.type == NoteBlockType.HEADING -> NoteBlockType.PARAGRAPH
                                            else -> block.type
                                        }
                                        if (index == 0) block.copy(type = type, spans = spansFromField(sliced))
                                        else NoteBlock(
                                            id = UUID.randomUUID().toString(),
                                            type = type,
                                            spans = spansFromField(sliced),
                                            checked = false,
                                            orderedIndex = if (type == NoteBlockType.NUMBERED) block.orderedIndex + index else 1,
                                            indentLevel = block.indentLevel
                                        )
                                    }
                                    val updatedBlocks = document.blocks.flatMap { existing ->
                                        if (existing.id == block.id) replacementBlocks else listOf(existing)
                                    }
                                    onDocumentChange(document.copy(blocks = updatedBlocks))
                                    val active = replacementBlocks.last()
                                    val lastText = active.spans.joinToString("") { it.text }
                                    val cursor = TextFieldValue(lastText, TextRange(lastText.length))
                                    activeBlockId = active.id
                                    activeField = cursor
                                    fields[active.id] = cursor
                                } else {
                                    field = next
                                    fields[block.id] = next
                                    activeBlockId = block.id
                                    activeField = next
                                    updateBlock(block.id) { it.copy(spans = spansFromField(next)) }
                                }
                            },
                            readOnly = readOnly,
                            textStyle = textStyle.copy(color = MaterialTheme.colorScheme.onSurface),
                            modifier = Modifier.weight(1f).fillMaxWidth()
                                .background(
                                    if (activeBlockId == block.id && !readOnly) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.background,
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 7.dp)
                                .onFocusChanged { if (it.isFocused) { activeBlockId = block.id; activeField = field } },
                            decorationBox = { inner ->
                                if (field.text.isEmpty() && document.blocks.size == 1) {
                                    Text("Write something…", color = MaterialTheme.colorScheme.onSurfaceVariant, style = textStyle)
                                }
                                inner()
                            }
                        )
                    }
                }
            }
            if (!readOnly) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                        IconButton(onClick = { addBlock() }) { Icon(Icons.Outlined.Add, "Add paragraph") }
                        Text("Add block", Modifier.align(Alignment.CenterVertically).clickable { addBlock() }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (!readOnly) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(14.dp),
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { changeActiveType(NoteBlockType.HEADING) }) { Icon(Icons.Outlined.Title, "Heading") }
                    IconButton(onClick = { applyMark(NoteMark.BOLD) }) { Icon(Icons.Outlined.FormatBold, "Bold") }
                    IconButton(onClick = { applyMark(NoteMark.ITALIC) }) { Icon(Icons.Outlined.FormatItalic, "Italic") }
                    IconButton(onClick = { applyMark(NoteMark.STRIKETHROUGH) }) { Icon(Icons.Outlined.FormatStrikethrough, "Strikethrough") }
                    IconButton(onClick = { applyMark(NoteMark.UNDERLINE) }) { Icon(Icons.Outlined.FormatUnderlined, "Underline") }
                    IconButton(onClick = { applyMark(NoteMark.CODE) }) { Icon(Icons.Outlined.Code, "Inline code") }
                    IconButton(onClick = { changeActiveType(NoteBlockType.CODE) }) { Icon(Icons.Outlined.Code, "Code block") }
                    IconButton(onClick = { showLinkDialog = true }) { Icon(Icons.Outlined.InsertLink, "Insert link") }
                    IconButton(onClick = { changeActiveType(NoteBlockType.BULLET) }) { Icon(Icons.Outlined.FormatListBulleted, "Bulleted list") }
                    IconButton(onClick = { changeActiveType(NoteBlockType.NUMBERED) }) { Icon(Icons.Outlined.FormatListNumbered, "Numbered list") }
                    IconButton(onClick = { changeActiveType(NoteBlockType.QUOTE) }) { Icon(Icons.Outlined.FormatQuote, "Quote") }
                    IconButton(onClick = { changeActiveType(NoteBlockType.CHECKLIST) }) { Icon(Icons.Outlined.CheckBox, "Checklist") }
                    IconButton(onClick = { changeIndent(-1) }) { Icon(Icons.Outlined.ChevronLeft, "Decrease indent") }
                    IconButton(onClick = { changeIndent(1) }) { Icon(Icons.Outlined.ChevronRight, "Increase indent") }
                    IconButton(onClick = { moveActiveBlock(-1) }) { Icon(Icons.Outlined.ArrowUpward, "Move block up") }
                    IconButton(onClick = { moveActiveBlock(1) }) { Icon(Icons.Outlined.ArrowDownward, "Move block down") }
                    IconButton(onClick = { deleteActiveBlock() }) { Icon(Icons.Outlined.DeleteOutline, "Delete block") }
                    IconButton(onClick = { changeActiveType(NoteBlockType.DIVIDER) }) { Icon(Icons.Outlined.HorizontalRule, "Divider") }
                    if (onInsertImage != null) {
                        IconButton(onClick = onInsertImage) {
                            Icon(Icons.Outlined.Add, "Insert attachment")
                        }
                    }
                }
            }
        }
    }
    if (showLinkDialog) {
        AlertDialog(
            onDismissRequest = { showLinkDialog = false },
            title = { Text("Insert link") },
            text = {
                OutlinedTextField(
                    value = linkUrl,
                    onValueChange = { linkUrl = it },
                    label = { Text("URL") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = { applyLink(linkUrl) }, enabled = linkUrl.isNotBlank()) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { showLinkDialog = false }) { Text("Cancel") } }
        )
    }
}

private fun sliceField(field: TextFieldValue, start: Int, end: Int): TextFieldValue {
    val safeStart = start.coerceIn(0, field.text.length)
    val safeEnd = end.coerceIn(safeStart, field.text.length)
    val sliced = buildAnnotatedString {
        append(field.text.substring(safeStart, safeEnd))
        field.annotatedString.spanStyles.forEach { range ->
            val overlapStart = maxOf(range.start, safeStart)
            val overlapEnd = minOf(range.end, safeEnd)
            if (overlapStart < overlapEnd) addStyle(range.item, overlapStart - safeStart, overlapEnd - safeStart)
        }
        field.annotatedString.getStringAnnotations("URL", safeStart, safeEnd).forEach { range ->
            val overlapStart = maxOf(range.start, safeStart)
            val overlapEnd = minOf(range.end, safeEnd)
            if (overlapStart < overlapEnd) addStringAnnotation("URL", range.item, overlapStart - safeStart, overlapEnd - safeStart)
        }
    }
    return TextFieldValue(sliced, TextRange(sliced.length))
}

private fun fieldFromBlock(block: NoteBlock, linkColor: androidx.compose.ui.graphics.Color): TextFieldValue {
    val annotated = buildAnnotatedString {
        block.spans.forEach { span ->
            val start = length
            append(span.text)
            val style = SpanStyle(
                fontWeight = if (NoteMark.BOLD in span.marks) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (NoteMark.ITALIC in span.marks) FontStyle.Italic else FontStyle.Normal,
                textDecoration = when {
                    NoteMark.STRIKETHROUGH in span.marks && (NoteMark.UNDERLINE in span.marks || span.link != null) ->
                        TextDecoration.combine(listOf(TextDecoration.LineThrough, TextDecoration.Underline))
                    NoteMark.STRIKETHROUGH in span.marks -> TextDecoration.LineThrough
                    NoteMark.UNDERLINE in span.marks || span.link != null -> TextDecoration.Underline
                    else -> null
                },
                fontFamily = if (NoteMark.CODE in span.marks) FontFamily.Monospace else FontFamily.Default,
                color = if (span.link != null) linkColor else androidx.compose.ui.graphics.Color.Unspecified
            )
            if (span.marks.isNotEmpty() || span.link != null) addStyle(style, start, length)
            if (span.link != null) addStringAnnotation("URL", span.link, start, length)
        }
    }
    return TextFieldValue(annotated, TextRange(annotated.length))
}

private fun spansFromField(field: TextFieldValue): List<NoteInline> {
    if (field.text.isEmpty()) return emptyList()
    val boundaries = sortedSetOf(0, field.text.length)
    field.annotatedString.spanStyles.forEach { range ->
        boundaries += range.start.coerceIn(0, field.text.length)
        boundaries += range.end.coerceIn(0, field.text.length)
    }
    field.annotatedString.getStringAnnotations("URL", 0, field.text.length).forEach { range ->
        boundaries += range.start.coerceIn(0, field.text.length)
        boundaries += range.end.coerceIn(0, field.text.length)
    }
    val points = boundaries.toList()
    return (0 until points.lastIndex).mapNotNull { index ->
        val start = points[index]
        val end = points[index + 1]
        if (start >= end) return@mapNotNull null
        val marks = field.annotatedString.spanStyles.filter { it.start <= start && it.end >= end }.flatMap { range ->
            buildList {
                if (range.item.fontWeight == FontWeight.Bold) add(NoteMark.BOLD)
                if (range.item.fontStyle == FontStyle.Italic) add(NoteMark.ITALIC)
                if (range.item.textDecoration?.contains(TextDecoration.LineThrough) == true) add(NoteMark.STRIKETHROUGH)
                if (range.item.textDecoration?.contains(TextDecoration.Underline) == true) add(NoteMark.UNDERLINE)
                if (range.item.fontFamily == FontFamily.Monospace) add(NoteMark.CODE)
            }
        }.toSet()
        val link = field.annotatedString.getStringAnnotations("URL", start, end)
            .firstOrNull { it.start <= start && it.end >= end }?.item
        NoteInline(field.text.substring(start, end), marks, link = link)
    }
}
