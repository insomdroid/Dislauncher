package com.insomdroid.dislauncher

import org.json.JSONArray
import org.json.JSONObject

/**
 * Structured, versioned note document model.
 *
 * Structured JSON is the canonical format. The legacy text parser is only used to migrate
 * notes created by older app versions; the active editor and archive format are structured.
 */
internal enum class NoteBlockType {
    PARAGRAPH, HEADING, BULLET, NUMBERED, QUOTE, CHECKLIST, CODE, DIVIDER
}

internal enum class NoteMark {
    BOLD, ITALIC, STRIKETHROUGH, CODE, UNDERLINE
}

internal data class NoteInline(
    val text: String,
    val marks: Set<NoteMark> = emptySet(),
    val link: String? = null,
    val imageUri: String? = null,
    val imageAlt: String = ""
)

internal data class NoteBlock(
    val id: String,
    val type: NoteBlockType,
    val spans: List<NoteInline> = emptyList(),
    val level: Int = 1,
    val checked: Boolean = false,
    val orderedIndex: Int = 1,
    val indentLevel: Int = 0
)

internal data class NoteDocument(
    val version: Int = CURRENT_VERSION,
    val blocks: List<NoteBlock> = emptyList()
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}

internal object NoteDocumentCodec {
    private val headingPattern = Regex("^(#{1,6})\\s+(.+)$")
    private val bulletPattern = Regex("^\\s*[-*+]\\s+(.+)$")
    private val numberedPattern = Regex("^\\s*(\\d+)[.)]\\s+(.+)$")
    private val checklistPattern = Regex("^\\s*[-*+]\\s+\\[([ xX])]\\s+(.+)$")
    private val inlinePattern = Regex("""\\(.)|<u>(.+?)</u>|!\[([^\]]*)\]\((?:<)?([^)>]+)(?:>)?\)|\[([^\]]+)\]\((https?://[^)]+)\)|\*\*\*(.+?)\*\*\*|\*\*(.+?)\*\*|~~(.+?)~~|\*(.+?)\*|`([^`]+)`""")

    fun fromLegacyContent(markdown: String): NoteDocument {
        if (markdown.isBlank()) return NoteDocument()
        val lines = markdown.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val blocks = mutableListOf<NoteBlock>()
        var index = 0
        fun add(type: NoteBlockType, text: String = "", level: Int = 1, checked: Boolean = false, orderedIndex: Int = 1) {
            val parsed = when {
                text.isEmpty() -> emptyList()
                type == NoteBlockType.CODE -> listOf(NoteInline(text))
                else -> parseInline(text)
            }
            if (parsed.none { it.imageUri != null }) {
                blocks += NoteBlock(
                    id = "b${blocks.size + 1}",
                    type = type,
                    spans = parsed,
                    level = level,
                    checked = checked,
                    orderedIndex = orderedIndex
                )
                return
            }
            val textSpans = mutableListOf<NoteInline>()
            fun flushText() {
                if (textSpans.isNotEmpty()) {
                    blocks += NoteBlock(
                        id = "b${blocks.size + 1}",
                        type = type,
                        spans = textSpans.toList(),
                        level = level,
                        checked = checked,
                        orderedIndex = orderedIndex
                    )
                    textSpans.clear()
                }
            }
            parsed.forEach { span ->
                if (span.imageUri != null) {
                    flushText()
                    blocks += NoteBlock(
                        id = "b${blocks.size + 1}",
                        type = NoteBlockType.PARAGRAPH,
                        spans = listOf(span)
                    )
                } else {
                    textSpans += span
                }
            }
            flushText()
        }
        fun isSpecial(line: String): Boolean =
            line.startsWith("```") || headingPattern.matches(line) || checklistPattern.matches(line) ||
                bulletPattern.matches(line) || numberedPattern.matches(line) ||
                line.trim() in setOf("---", "***", "___") || line.trimStart().startsWith(">")

        while (index < lines.size) {
            val line = lines[index]
            if (line.isBlank()) {
                index++
                continue
            }
            val headingMatch = headingPattern.matchEntire(line)
            val checklistMatch = checklistPattern.matchEntire(line)
            val bulletMatch = bulletPattern.matchEntire(line)
            val numberedMatch = numberedPattern.matchEntire(line)
            when {
                line.startsWith("```") -> {
                    index++
                    val code = mutableListOf<String>()
                    while (index < lines.size && !lines[index].startsWith("```")) {
                        code += lines[index++]
                    }
                    if (index < lines.size) index++
                    add(NoteBlockType.CODE, code.joinToString("\n"))
                }
                headingMatch != null -> {
                    add(NoteBlockType.HEADING, headingMatch.groupValues[2], headingMatch.groupValues[1].length)
                    index++
                }
                checklistMatch != null -> {
                    add(NoteBlockType.CHECKLIST, checklistMatch.groupValues[2], checked = checklistMatch.groupValues[1].lowercase() == "x")
                    index++
                }
                bulletMatch != null -> {
                    add(NoteBlockType.BULLET, bulletMatch.groupValues[1])
                    index++
                }
                numberedMatch != null -> {
                    add(NoteBlockType.NUMBERED, numberedMatch.groupValues[2], orderedIndex = numberedMatch.groupValues[1].toIntOrNull() ?: 1)
                    index++
                }
                line.trim() in setOf("---", "***", "___") -> {
                    add(NoteBlockType.DIVIDER)
                    index++
                }
                line.trimStart().startsWith(">") -> {
                    add(NoteBlockType.QUOTE, line.trimStart().removePrefix(">").removePrefix(" "))
                    index++
                }
                else -> {
                    val paragraph = mutableListOf(line)
                    index++
                    while (index < lines.size && lines[index].isNotBlank() && !isSpecial(lines[index])) {
                        paragraph += lines[index++]
                    }
                    add(NoteBlockType.PARAGRAPH, paragraph.joinToString("\n"))
                }
            }
        }
        return NoteDocument(blocks = blocks)
    }

    private fun parseInline(text: String): List<NoteInline> {
        if (text.isEmpty()) return emptyList()
        val result = mutableListOf<NoteInline>()
        var cursor = 0
        inlinePattern.findAll(text).forEach { match ->
            if (match.range.first > cursor) result += NoteInline(text.substring(cursor, match.range.first))
            val g = match.groupValues
            when {
                g[1].isNotEmpty() -> result += NoteInline(g[1])
                g[2].isNotEmpty() -> result += NoteInline(g[2], setOf(NoteMark.UNDERLINE))
                match.value.startsWith("![") -> result += NoteInline(
                    text = g[3],
                    imageUri = g[4],
                    imageAlt = g[3]
                )
                match.value.startsWith("[") -> result += NoteInline(text = g[5], link = g[6])
                g[7].isNotEmpty() -> result += NoteInline(g[7], setOf(NoteMark.BOLD, NoteMark.ITALIC))
                g[8].isNotEmpty() -> result += NoteInline(g[8], setOf(NoteMark.BOLD))
                g[9].isNotEmpty() -> result += NoteInline(g[9], setOf(NoteMark.STRIKETHROUGH))
                g[10].isNotEmpty() -> result += NoteInline(g[10], setOf(NoteMark.ITALIC))
                g[11].isNotEmpty() -> result += NoteInline(g[11], setOf(NoteMark.CODE))
            }
            cursor = match.range.last + 1
        }
        if (cursor < text.length) result += NoteInline(text.substring(cursor))
        return result
    }

    fun plainText(document: NoteDocument): String = document.blocks.joinToString("\n") { block ->
        val text = block.spans.joinToString("") { span -> span.text.ifBlank { if (span.imageUri != null) "[Image]" else "" } }
        when (block.type) {
            NoteBlockType.BULLET -> "• $text"
            NoteBlockType.NUMBERED -> "${block.orderedIndex}. $text"
            NoteBlockType.CHECKLIST -> "${if (block.checked) "☑" else "☐"} $text"
            NoteBlockType.QUOTE -> "▏ $text"
            NoteBlockType.DIVIDER -> "────────"
            else -> text
        }
    }

    /** Serialize a structured note as portable Markdown. */
    fun toMarkdown(document: NoteDocument): String {
        fun escapeText(value: String): String = value.replace("\\", "\\\\").replace("*", "\\*").replace("_", "\\_").replace("~", "\\~").replace("`", "\\`").replace("[", "\\[").replace("]", "\\]")
        fun inline(spans: List<NoteInline>): String = spans.joinToString("") { span ->
            when {
                span.imageUri != null -> "![" + span.imageAlt.ifBlank { span.text }.replace("]", "\\]") + "](" + span.imageUri + ")"
                span.link != null -> "[" + escapeText(span.text) + "](" + span.link.replace(")", "%29") + ")"
                else -> {
                    var value = if (NoteMark.CODE in span.marks) "`" + span.text.replace("`", "\\`") + "`" else escapeText(span.text)
                    if (NoteMark.BOLD in span.marks) value = "**" + value + "**"
                    if (NoteMark.ITALIC in span.marks) value = "*" + value + "*"
                    if (NoteMark.STRIKETHROUGH in span.marks) value = "~~" + value + "~~"
                    if (NoteMark.UNDERLINE in span.marks) value = "<u>" + value + "</u>"
                    value
                }
            }
        }
        return document.blocks.joinToString("\n\n") { block ->
            val value = inline(block.spans)
            when (block.type) {
                NoteBlockType.HEADING -> "#".repeat(block.level.coerceIn(1, 6)) + " " + value
                NoteBlockType.BULLET -> "  ".repeat(block.indentLevel) + "- " + value
                NoteBlockType.NUMBERED -> "  ".repeat(block.indentLevel) + block.orderedIndex + ". " + value
                NoteBlockType.CHECKLIST -> "  ".repeat(block.indentLevel) + "- [" + (if (block.checked) "x" else " ") + "] " + value
                NoteBlockType.QUOTE -> value.lines().joinToString("\n") { "> " + it }
                NoteBlockType.CODE -> { val code = block.spans.joinToString("") { it.text }; val fence = if (code.contains("```") ) "````" else "```"; fence + "\n" + code + "\n" + fence }
                NoteBlockType.DIVIDER -> "---"
                NoteBlockType.PARAGRAPH -> value
            }
        }
    }
    fun encode(document: NoteDocument): String {
        val root = JSONObject().put("version", document.version)
        val blocks = JSONArray()
        document.blocks.forEach { block ->
            val spans = JSONArray()
            block.spans.forEach { span ->
                val marks = JSONArray()
                span.marks.forEach { marks.put(it.name) }
                spans.put(JSONObject()
                    .put("text", span.text)
                    .put("marks", marks)
                    .put("link", span.link)
                    .put("imageUri", span.imageUri)
                    .put("imageAlt", span.imageAlt))
            }
            blocks.put(JSONObject()
                .put("id", block.id)
                .put("type", block.type.name)
                .put("spans", spans)
                .put("level", block.level)
                .put("checked", block.checked)
                .put("orderedIndex", block.orderedIndex)
                .put("indentLevel", block.indentLevel))
        }
        return root.put("blocks", blocks).toString()
    }

    fun decode(json: String): NoteDocument? = runCatching {
        val root = JSONObject(json)
        val version = root.optInt("version", 0)
        require(version in 1..NoteDocument.CURRENT_VERSION) { "Unsupported note document version: $version" }
        val array = root.optJSONArray("blocks") ?: JSONArray()
        val blocks = (0 until array.length()).mapNotNull { i ->
            val item = array.optJSONObject(i) ?: return@mapNotNull null
            val type = runCatching { NoteBlockType.valueOf(item.optString("type")) }.getOrNull()
                ?: return@mapNotNull null
            val spanArray = item.optJSONArray("spans") ?: JSONArray()
            val spans = (0 until spanArray.length()).mapNotNull { j ->
                val span = spanArray.optJSONObject(j) ?: return@mapNotNull null
                val markArray = span.optJSONArray("marks") ?: JSONArray()
                val marks = (0 until markArray.length()).mapNotNull { k ->
                    runCatching { NoteMark.valueOf(markArray.optString(k)) }.getOrNull()
                }.toSet()
                NoteInline(
                    text = span.optString("text"),
                    marks = marks,
                    link = span.optString("link").takeIf { it.isNotBlank() && it != "null" },
                    imageUri = span.optString("imageUri").takeIf { it.isNotBlank() && it != "null" },
                    imageAlt = span.optString("imageAlt")
                )
            }
            NoteBlock(
                id = item.optString("id").ifBlank { "b${i + 1}" },
                type = type,
                spans = spans,
                level = item.optInt("level", 1).coerceIn(1, 6),
                checked = item.optBoolean("checked", false),
                orderedIndex = item.optInt("orderedIndex", 1).coerceAtLeast(1),
                indentLevel = item.optInt("indentLevel", 0).coerceIn(0, 5)
            )
        }
        NoteDocument(version = version, blocks = blocks)
    }.getOrNull()
}
