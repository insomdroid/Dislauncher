package com.insomdroid.dislauncher

import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.flow.distinctUntilChanged
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
@OptIn(ExperimentalFoundationApi::class)
internal fun MarkdownNoteEditor(
    value: String,
    selection: Int,
    onValueChange: (String) -> Unit,
    onSelectionChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onInsertImage: (() -> Unit)? = null,
    enabled: Boolean = true
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val colors = MaterialTheme.colorScheme
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnSelectionChange by rememberUpdatedState(onSelectionChange)
    var preview by remember { mutableStateOf(false) }
    val field = rememberTextFieldState(
        initialText = value,
        initialSelection = TextRange(selection.coerceIn(0, value.length))
    )
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
        if (field.text.toString() != value || field.selection.end != safeSelection) {
            field.edit {
                replace(0, length, value)
                this.selection = TextRange(safeSelection)
            }
        }
    }

    LaunchedEffect(field) {
        snapshotFlow { field.text.toString() to field.selection.end }
            .distinctUntilChanged()
            .collect { (text, cursor) ->
                currentOnValueChange(text)
                currentOnSelectionChange(cursor)
            }
    }

    val markdownInputTransformation = remember {
        InputTransformation {
            if (changes.changeCount == 1) {
                val insertedRange = changes.getRange(0)
                val replacedRange = changes.getOriginalRange(0)
                val currentText = asCharSequence()
                val insertedNewline = replacedRange.collapsed &&
                    insertedRange.length == 1 &&
                    currentText.getOrNull(insertedRange.start) == '\n'
                if (insertedNewline) {
                    val cursorBeforeNewline = insertedRange.start
                    val lineStart = if (cursorBeforeNewline == 0) 0
                        else currentText.lastIndexOf('\n', cursorBeforeNewline - 1) + 1
                    val line = currentText.subSequence(lineStart, cursorBeforeNewline).toString()
                    val match = MARKDOWN_LIST_LINE_REGEX.matchEntire(line)
                    if (match != null) {
                        val indent = match.groupValues[1]
                        val marker = match.groupValues[2]
                        val content = match.groupValues[3]
                        if (content.isBlank()) {
                            replace(lineStart, insertedRange.end, "")
                            this.selection = TextRange(lineStart)
                        } else {
                            val nextMarker = if (marker.trimStart().firstOrNull()?.isDigit() == true) {
                                val number = MARKDOWN_NUMBER_REGEX.find(marker)?.value?.toIntOrNull() ?: 1
                                marker.replace(MARKDOWN_NUMBER_REGEX, (number + 1).toString())
                            } else marker
                            val continuation = indent + nextMarker
                            replace(insertedRange.end, insertedRange.end, continuation)
                            this.selection = TextRange(insertedRange.end + continuation.length)
                        }
                    }
                }
            }
        }
    }

    fun replaceSelection(before: String, after: String, placeholder: String = "text") {
        field.edit {
            val start = minOf(this.selection.start, this.selection.end).coerceIn(0, length)
            val end = maxOf(this.selection.start, this.selection.end).coerceIn(start, length)
            val selected = asCharSequence().subSequence(start, end).toString().ifEmpty { placeholder }
            replace(start, end, before + selected + after)
            val selectionStart = start + before.length
            this.selection = TextRange(selectionStart, selectionStart + selected.length)
        }
    }

    fun prefixLine(prefix: String) {
        field.edit {
            val start = minOf(this.selection.start, this.selection.end).coerceIn(0, length)
            val end = maxOf(this.selection.start, this.selection.end).coerceIn(start, length)
            val currentText = asCharSequence()
            val lineStart = if (start == 0) 0 else currentText.lastIndexOf('\n', start - 1).let { it + 1 }
            replace(lineStart, lineStart, prefix)
            this.selection = TextRange(start + prefix.length, end + prefix.length)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = if (isLandscape) 0.dp else 4.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { preview = false }, enabled = enabled && preview) { Text("Edit") }
            TextButton(onClick = { preview = true }, enabled = enabled && !preview) { Text("Preview") }
        }
        if (!preview) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = if (isLandscape) 1.dp else 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                MarkdownToolButton("B", enabled = enabled) { replaceSelection("**", "**") }
                MarkdownToolButton("I", enabled = enabled) { replaceSelection("*", "*") }
                MarkdownToolButton("S", enabled = enabled) { replaceSelection("~~", "~~") }
                MarkdownToolButton("H2", enabled = enabled) { prefixLine("## ") }
                MarkdownToolButton("• List", enabled = enabled) { prefixLine("- ") }
                MarkdownToolButton("1. List", enabled = enabled) { prefixLine("1. ") }
                MarkdownToolButton("☐ Task", enabled = enabled) { prefixLine("- [ ] ") }
                MarkdownToolButton("> Quote", enabled = enabled) { prefixLine("> ") }
                MarkdownToolButton("`Code`", enabled = enabled) { replaceSelection("`", "`") }
                MarkdownToolButton("Link", enabled = enabled) { replaceSelection("[", "](https://)", "link text") }
                MarkdownToolButton("---", enabled = enabled) {
                    field.edit {
                        val start = minOf(this.selection.start, this.selection.end).coerceIn(0, length)
                        replace(start, start, "---\n")
                        this.selection = TextRange(start + 4)
                    }
                }
                if (onInsertImage != null) MarkdownToolButton("Image", enabled = enabled) { onInsertImage() }
            }
            BasicTextField(
                state = field,
                enabled = enabled,
                inputTransformation = markdownInputTransformation,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = if (isLandscape) 2.dp else 10.dp)
                    .onPreviewKeyEvent { event ->
                        if (!enabled) return@onPreviewKeyEvent false
                        if (event.type != KeyEventType.KeyDown || event.key != Key.Enter || !field.selection.collapsed) {
                            return@onPreviewKeyEvent false
                        }
                        val text = field.text.toString()
                        val cursor = minOf(field.selection.start, field.selection.end).coerceIn(0, text.length)
                        val lineStart = if (cursor == 0) 0 else text.lastIndexOf('\n', cursor - 1) + 1
                        val lineEnd = text.indexOf('\n', cursor).let { if (it < 0) text.length else it }
                        val line = text.substring(lineStart, lineEnd)
                        val match = MARKDOWN_LIST_LINE_REGEX.matchEntire(line)
                            ?: return@onPreviewKeyEvent false
                        val indent = match.groupValues[1]
                        val marker = match.groupValues[2]
                        val content = match.groupValues[3]
                        field.edit {
                            if (content.isBlank()) {
                                replace(lineStart, lineEnd, "")
                                this.selection = TextRange(lineStart)
                            } else {
                                val nextMarker = if (marker.trimStart().firstOrNull()?.isDigit() == true) {
                                    val number = MARKDOWN_NUMBER_REGEX.find(marker)?.value?.toIntOrNull() ?: 1
                                    marker.replace(MARKDOWN_NUMBER_REGEX, (number + 1).toString())
                                } else marker
                                val insertion = "\n" + indent + nextMarker
                                replace(cursor, cursor, insertion)
                                this.selection = TextRange(cursor + insertion.length)
                            }
                        }
                        true
                    },
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = colors.onSurface,
                    fontFamily = FontFamily.Monospace
                ),
                cursorBrush = SolidColor(colors.primary),
                decorator = { inner ->
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
                    if (scrollView.tag != field.text.toString()) {
                        markwon.setMarkdown(textView, field.text.toString())
                        scrollView.tag = field.text.toString()
                    }
                }
            )
        }
    }
}

@Composable
private fun MarkdownToolButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(LauncherShapeTokens.medium),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        enabled = enabled
    ) {
        Text(label, Modifier.padding(horizontal = 10.dp, vertical = if (isLandscape) 5.dp else 8.dp), style = MaterialTheme.typography.labelLarge)
    }
}
