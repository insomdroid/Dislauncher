package com.insomdroid.dislauncher

import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.LinkResolver
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.ImageItem
import io.noties.markwon.image.ImagesPlugin
import io.noties.markwon.image.SchemeHandler

private val MARKDOWN_LIST_LINE_REGEX: Regex = Regex("^(\\s*)([-*+]\\s+|\\d+[.)]\\s+)(.*)$")
private val MARKDOWN_NUMBER_REGEX: Regex = Regex("\\d+")

/** Markdown-native editor. The Markdown source is canonical; preview is rendered locally. */
@Composable
internal fun MarkdownNoteEditor(
    value: String,
    selection: Int,
    onValueChange: (String) -> Unit,
    onSelectionChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onInsertImage: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    var preview by remember { mutableStateOf(false) }
    var field by remember { mutableStateOf(TextFieldValue(value, selection = TextRange(selection.coerceIn(0, value.length)))) }
    val markwon = remember(context) {
        Markwon.builder(context)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TaskListPlugin.create(context))
            .usePlugin(HtmlPlugin.create())
            .usePlugin(ImagesPlugin.create(object : ImagesPlugin.ImagesConfigure {
                override fun configureImages(plugin: ImagesPlugin) {
                    plugin.addSchemeHandler(object : SchemeHandler() {
                        override fun handle(raw: String, uri: Uri): ImageItem {
                            val input = context.contentResolver.openInputStream(uri)
                                ?: error("Unable to open note image")
                            val mimeType = context.contentResolver.getType(uri) ?: "image/*"
                            return ImageItem.withDecodingNeeded(mimeType, input)
                        }

                        override fun supportedSchemes(): Collection<String> = listOf("content")
                    })
                }
            }))
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                    builder.linkResolver(object : LinkResolver {
                        override fun resolve(view: android.view.View, link: String) {
                            val uri = runCatching { Uri.parse(link) }.getOrNull()
                            if (uri != null && (
                                uri.scheme.equals("http", ignoreCase = true) ||
                                    uri.scheme.equals("https", ignoreCase = true)
                                )
                            ) {
                                runCatching {
                                    view.context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                }
                            }
                        }
                    })
                }
            })
            .build()
    }

    LaunchedEffect(value, selection) {
        val safeSelection = selection.coerceIn(0, value.length)
        if (field.text != value || field.selection.end != safeSelection) {
            field = TextFieldValue(value, selection = TextRange(safeSelection))
        }
    }

    fun replaceSelection(before: String, after: String, placeholder: String = "text") {
        val start = field.selection.min.coerceIn(0, field.text.length)
        val end = field.selection.max.coerceIn(start, field.text.length)
        val selected = field.text.substring(start, end).ifEmpty { placeholder }
        val nextText = field.text.replaceRange(start, end, before + selected + after)
        val selectionStart = start + before.length
        field = TextFieldValue(nextText, selection = TextRange(selectionStart, selectionStart + selected.length))
        onValueChange(nextText)
        onSelectionChange(field.selection.end)
    }

    fun prefixLine(prefix: String) {
        val start = field.selection.min.coerceIn(0, field.text.length)
        val lineStart = if (start == 0) 0 else field.text.lastIndexOf('\n', start - 1).let { it + 1 }
        val nextText = field.text.substring(0, lineStart) + prefix + field.text.substring(lineStart)
        field = TextFieldValue(nextText, selection = TextRange(start + prefix.length, field.selection.max + prefix.length))
        onValueChange(nextText)
        onSelectionChange(field.selection.end)
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { preview = false }, enabled = preview) { Text("Edit") }
            TextButton(onClick = { preview = true }, enabled = !preview) { Text("Preview") }
        }
        if (!preview) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                MarkdownToolButton("B") { replaceSelection("**", "**") }
                MarkdownToolButton("I") { replaceSelection("*", "*") }
                MarkdownToolButton("S") { replaceSelection("~~", "~~") }
                MarkdownToolButton("H2") { prefixLine("## ") }
                MarkdownToolButton("• List") { prefixLine("- ") }
                MarkdownToolButton("1. List") { prefixLine("1. ") }
                MarkdownToolButton("☐ Task") { prefixLine("- [ ] ") }
                MarkdownToolButton("> Quote") { prefixLine("> ") }
                MarkdownToolButton("`Code`") { replaceSelection("`", "`") }
                MarkdownToolButton("Link") { replaceSelection("[", "](https://)", "link text") }
                MarkdownToolButton("---") {
                    val start = field.selection.min.coerceIn(0, field.text.length)
                    val next = field.text.replaceRange(start, start, "---\n")
                    field = TextFieldValue(next, selection = TextRange(start + 4))
                    onValueChange(next)
                    onSelectionChange(field.selection.end)
                }
                if (onInsertImage != null) MarkdownToolButton("Image") { onInsertImage() }
            }
            BasicTextField(
                value = field,
                onValueChange = { incoming ->
                    val previous = field
                    val cursorAfterNewline = incoming.selection.min
                    val insertedNewline = incoming.text.length == previous.text.length + 1 &&
                        cursorAfterNewline > 0 && incoming.text.getOrNull(cursorAfterNewline - 1) == '\n'
                    if (insertedNewline && previous.selection.collapsed) {
                        val cursorBeforeNewline = previous.selection.min.coerceIn(0, previous.text.length)
                        val lineStart = if (cursorBeforeNewline == 0) 0 else previous.text.lastIndexOf('\n', cursorBeforeNewline - 1) + 1
                        val line = previous.text.substring(lineStart, cursorBeforeNewline)
                        val match = MARKDOWN_LIST_LINE_REGEX.matchEntire(line)
                        if (match != null) {
                            val indent = match.groupValues[1]
                            val marker = match.groupValues[2]
                            val content = match.groupValues[3]
                            if (content.isBlank()) {
                                val nextText = incoming.text.removeRange(lineStart, cursorBeforeNewline + 1)
                                field = TextFieldValue(nextText, selection = TextRange(lineStart))
                                onValueChange(nextText)
                                onSelectionChange(field.selection.end)
                            } else {
                                val nextMarker = if (marker.trimStart().firstOrNull()?.isDigit() == true) {
                                    val number = MARKDOWN_NUMBER_REGEX.find(marker)?.value?.toIntOrNull() ?: 1
                                    marker.replace(MARKDOWN_NUMBER_REGEX, (number + 1).toString())
                                } else marker
                                val continuation = indent + nextMarker
                                val insertionPoint = cursorAfterNewline
                                val nextText = incoming.text.replaceRange(insertionPoint, insertionPoint, continuation)
                                field = TextFieldValue(nextText, selection = TextRange(insertionPoint + continuation.length))
                                onValueChange(nextText)
                                onSelectionChange(field.selection.end)
                            }
                            return@BasicTextField
                        }
                    }
                    field = incoming
                    onValueChange(incoming.text)
                    onSelectionChange(incoming.selection.end)
                },
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown || event.key != Key.Enter || !field.selection.collapsed) {
                            return@onPreviewKeyEvent false
                        }
                        val text = field.text
                        val cursor = field.selection.min.coerceIn(0, text.length)
                        val lineStart = if (cursor == 0) 0 else text.lastIndexOf('\n', cursor - 1) + 1
                        val lineEnd = text.indexOf('\n', cursor).let { if (it < 0) text.length else it }
                        val line = text.substring(lineStart, lineEnd)
                        val match = MARKDOWN_LIST_LINE_REGEX.matchEntire(line)
                            ?: return@onPreviewKeyEvent false
                        val indent = match.groupValues[1]
                        val marker = match.groupValues[2]
                        val content = match.groupValues[3]
                        if (content.isBlank()) {
                            val nextText = text.removeRange(lineStart, lineEnd)
                            field = TextFieldValue(nextText, selection = TextRange(lineStart))
                            onValueChange(nextText)
                            onSelectionChange(field.selection.end)
                        } else {
                            val nextMarker = if (marker.trimStart().firstOrNull()?.isDigit() == true) {
                                val number = MARKDOWN_NUMBER_REGEX.find(marker)?.value?.toIntOrNull() ?: 1
                                marker.replace(MARKDOWN_NUMBER_REGEX, (number + 1).toString())
                            } else marker
                            val insertion = "\n" + indent + nextMarker
                            val nextText = text.replaceRange(cursor, cursor, insertion)
                            field = TextFieldValue(nextText, selection = TextRange(cursor + insertion.length))
                            onValueChange(nextText)
                            onSelectionChange(field.selection.end)
                        }
                        true
                    },
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = colors.onSurface,
                    fontFamily = FontFamily.Monospace
                ),
                cursorBrush = SolidColor(colors.primary),
                decorationBox = { inner ->
                    Column {
                        if (field.text.isEmpty()) {
                            Text(
                                "Write in Markdown…\n\n# Heading\n**Bold** · *Italic* · [Link](https://…)",
                                color = colors.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        inner()
                    }
                }
            )
        } else {
            AndroidView(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).clipToBounds(),
                factory = { viewContext ->
                    ScrollView(viewContext).apply {
                        // Give Preview its own opaque surface and a clipped, bounded viewport.
                        // This prevents the screen behind it from showing through while scrolling.
                        setBackgroundColor(colors.surface.toArgb())
                        isFillViewport = true
                        clipChildren = true
                        clipToPadding = true
                        overScrollMode = android.view.View.OVER_SCROLL_NEVER
                        isNestedScrollingEnabled = false
                        addView(
                            TextView(viewContext).apply {
                                setTextColor(colors.onSurface.copy(alpha = 1f).toArgb())
                                textSize = 16f
                                setPadding(0, 0, 0, 0)
                                layoutParams = FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT
                                )
                            }
                        )
                    }
                },
                update = { scrollView ->
                    val textView = scrollView.getChildAt(0) as TextView
                    textView.setTextColor(colors.onSurface.copy(alpha = 1f).toArgb())

                    // Re-render only when the Markdown source actually changes.
                    // Theme/recomposition updates should not parse the whole document again.
                    if (scrollView.tag != field.text) {
                        markwon.setMarkdown(textView, field.text)
                        scrollView.tag = field.text
                    }
                }
            )
        }
    }
}

@Composable
private fun MarkdownToolButton(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(LauncherShapeTokens.medium),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Text(label, Modifier.padding(horizontal = 10.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge)
    }
}
