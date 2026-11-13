package de.utrost.textr

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.core.StreamReadConstraints
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.HtmlBlock
import org.commonmark.node.HtmlInline
import org.commonmark.node.Image
import org.commonmark.node.Text
import org.commonmark.node.Node
import org.commonmark.node.Heading
import org.commonmark.node.Code
import org.commonmark.renderer.html.AttributeProvider
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import java.io.StringWriter

data class OutlineHeading(val id: String, val title: String, val level: Int)

data class PreparedDocument(val source: TextDocument, val preview: String?, val notice: PreviewNotice = PreviewNotice.NONE, val headings: List<OutlineHeading> = emptyList()) {
    val modes: List<DisplayMode> get() = buildList {
        add(DisplayMode.PLAIN)
        if (source.kind !in setOf(FileKind.TEXT, FileKind.CSV) && source.text.length <= RenderPolicy.MAX_HIGHLIGHT_CHARS) add(DisplayMode.HIGHLIGHTED)
        if (preview != null) add(DisplayMode.PREVIEW)
    }
    val defaultMode: DisplayMode get() = if (source.kind == FileKind.CSV) DisplayMode.PLAIN else modes.last()
    val fingerprint: String by lazy { java.security.MessageDigest.getInstance("SHA-256").digest(source.text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) } }
}

object DocumentRenderer {
    private val json = JsonFactory.builder().streamReadConstraints(
        StreamReadConstraints.builder().maxNestingDepth(128)
            .maxStringLength(MAX_FILE_BYTES).maxNumberLength(MAX_FILE_BYTES).build()
    ).build()

    fun prepare(document: TextDocument, csv: CsvOptions = CsvOptions()): PreparedDocument {
        RenderPolicy.checkCancellation()
        if (document.kind == FileKind.JSON && document.bytes > RenderPolicy.MAX_JSON_PREVIEW_BYTES) {
            return PreparedDocument(document, null, PreviewNotice.JSON_LIMIT)
        }
        if (document.kind == FileKind.MARKDOWN && document.text.length > RenderPolicy.MAX_MARKDOWN_CHARS) {
            return PreparedDocument(document, null, PreviewNotice.MARKDOWN_LIMIT)
        }
        if (document.kind == FileKind.ICALENDAR && document.text.length > CalendarPreview.MAX_CHARS) {
            return PreparedDocument(document, null, PreviewNotice.CALENDAR_LIMIT)
        }
        val headings = mutableListOf<OutlineHeading>()
        val preview = try {
            when (document.kind) {
                FileKind.JSON -> prettyJson(document.text)?.let { "<pre>${SyntaxHighlighter.escape(it)}</pre>" }
                FileKind.MARKDOWN -> markdown(document.text, headings)
                FileKind.ICALENDAR -> CalendarPreview.render(document.text)
                FileKind.CSV -> CsvPreview.render(document.text, csv)
                else -> null
            }
        } catch (_: StackOverflowError) {
            // Deeply nested Markdown may exceed the renderer's stack; source is still usable.
            null
        } catch (error: RuntimeException) {
            if (error is java.util.concurrent.CancellationException) throw error
            null
        }
        RenderPolicy.checkCancellation()
        val notice = if (preview == null && document.kind == FileKind.CSV) PreviewNotice.CSV_UNAVAILABLE else if (preview == null && document.kind != FileKind.TEXT && document.text.length > RenderPolicy.MAX_HIGHLIGHT_CHARS) {
            PreviewNotice.HIGHLIGHT_LIMIT
        } else if (preview == null && document.kind in setOf(FileKind.MARKDOWN, FileKind.JSON, FileKind.ICALENDAR)) {
            PreviewNotice.UNAVAILABLE
        } else PreviewNotice.NONE
        return PreparedDocument(document, preview, notice, if (preview != null) headings else emptyList())
    }

    fun prettyJson(text: String): String? = try {
        json.createParser(text).use { parser ->
            if (parser.nextToken() == null) return null
            val output = object : StringWriter() {
                override fun write(c: Int) {
                    RenderPolicy.checkCancellation()
                    require(buffer.length < RenderPolicy.MAX_PREVIEW_CHARS)
                    super.write(c)
                }
                override fun write(str: String) = write(str, 0, str.length)
                override fun write(str: String, off: Int, len: Int) {
                    RenderPolicy.checkCancellation()
                    require(buffer.length + len <= RenderPolicy.MAX_PREVIEW_CHARS)
                    super.write(str, off, len)
                }
                override fun write(cbuf: CharArray, off: Int, len: Int) {
                    RenderPolicy.checkCancellation()
                    require(buffer.length + len <= RenderPolicy.MAX_PREVIEW_CHARS)
                    super.write(cbuf, off, len)
                }
            }
            json.createGenerator(output).use { generator ->
                generator.useDefaultPrettyPrinter()
                var depth = 0
                do {
                    RenderPolicy.checkCancellation()
                    val token = parser.currentToken()
                    when (token) {
                        JsonToken.START_OBJECT, JsonToken.START_ARRAY -> { depth++; generator.copyCurrentEvent(parser) }
                        JsonToken.END_OBJECT, JsonToken.END_ARRAY -> { depth--; generator.copyCurrentEvent(parser) }
                        // Preserve numeric spelling and precision; do not coerce through Double.
                        JsonToken.VALUE_NUMBER_INT, JsonToken.VALUE_NUMBER_FLOAT -> generator.writeNumber(parser.text)
                        else -> generator.copyCurrentEvent(parser)
                    }
                    if (depth == 0) break
                } while (parser.nextToken() != null)
                if (depth != 0 || parser.nextToken() != null) return null
            }
            output.toString()
        }
    } catch (_: java.io.IOException) { null }
      catch (_: IllegalArgumentException) { null }

    fun markdown(source: String, headings: MutableList<OutlineHeading> = mutableListOf()): String {
        require(source.length <= RenderPolicy.MAX_MARKDOWN_CHARS)
        RenderPolicy.checkCancellation()
        val extensions = listOf(TablesExtension.create())
        val node = Parser.builder().extensions(extensions).build().parse(source)
        // Visit iteratively so adversarial nesting cannot exhaust our own stack.
        val pending = ArrayDeque<Pair<Node, Int>>()
        pending.add(node to 0)
        val headingIds = java.util.IdentityHashMap<Node, String>()
        var count = 0
        while (pending.isNotEmpty()) {
            RenderPolicy.checkCancellation()
            val (current, depth) = pending.removeLast()
            require(++count <= RenderPolicy.MAX_MARKDOWN_NODES && depth <= RenderPolicy.MAX_MARKDOWN_DEPTH)
            if (current is Heading) {
                val label = StringBuilder()
                val children = ArrayDeque<Node>()
                current.firstChild?.let(children::add)
                while (children.isNotEmpty() && label.length < 160) {
                    val child = children.removeLast()
                    when (child) {
                        is Text -> label.append(child.literal)
                        is Code -> label.append(child.literal)
                        is org.commonmark.node.SoftLineBreak, is org.commonmark.node.HardLineBreak -> label.append(' ')
                    }
                    child.next?.let(children::add)
                    child.firstChild?.let(children::add)
                }
                val id = "heading-${headings.size + 1}"
                headingIds[current] = id
                headings.add(OutlineHeading(id, label.toString().take(160).ifBlank { "Untitled heading" }, current.level))
            }
            when (current) {
                is Image -> { current.insertBefore(Text("[image omitted]")); current.unlink() }
                is HtmlBlock, is HtmlInline -> current.unlink()
                else -> {
                    var child = current.lastChild
                    while (child != null) {
                        pending.add(child to depth + 1)
                        child = child.previous
                    }
                }
            }
        }
        return HtmlRenderer.builder().extensions(extensions).escapeHtml(true)
            .sanitizeUrls(true).attributeProviderFactory {
                AttributeProvider { current, _, attributes -> headingIds[current]?.let { attributes["id"] = it } }
            }.build().render(node)
    }

    /** Each request gets a fresh stream; plain text is escaped in small chunks. */
    fun page(document: PreparedDocument, mode: DisplayMode, dark: Boolean, wrap: Boolean, lineNumbers: Boolean = false, targetLine: Int = 0, match: SourceMatch? = null): HtmlPage {
        val plain = mode == DisplayMode.PLAIN || (mode == DisplayMode.PREVIEW && document.preview == null)
        val content = if (plain) document.source.text else when (mode) {
            DisplayMode.PREVIEW -> document.preview!!
            DisplayMode.HIGHLIGHTED -> SyntaxHighlighter.highlight(document.source.text, document.source.kind)
            else -> error("Plain text handled above")
        }
        val source = mode != DisplayMode.PREVIEW || plain
        val numbered = source && lineNumbers && logicalLines(document.source.text) <= MAX_NUMBERED_LINES
        return HtmlPage(header(dark, wrap) + if (source) (if (numbered) "<pre class=\"numbered\">" else "<pre>") else "", content,
            (if (source) "</pre>" else "") + "</body></html>", plain, plain && wrap, numbered, if (source) targetLine else 0, match.takeIf { plain })
    }

    fun html(document: PreparedDocument, mode: DisplayMode, dark: Boolean, wrap: Boolean): String =
        page(document, mode, dark, wrap).openStream().bufferedReader(Charsets.UTF_8).use { it.readText() }

    const val MAX_NUMBERED_LINES = 10_000
    fun logicalLines(text: String): Int = 1 + text.indices.count { text[it] == '\n' || (text[it] == '\r' && text.getOrNull(it + 1) != '\n') }

    private fun header(dark: Boolean, wrap: Boolean): String {
        val background = if (dark) "#171c1a" else "#fafaf7"
        val foreground = if (dark) "#e3e9e5" else "#202923"
        return """<!doctype html><html><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'; img-src 'none'; script-src 'none'; base-uri 'none'; form-action 'none'">
            <style>
            :root { color-scheme: ${if (dark) "dark" else "light"}; }
            body { margin: 0; padding: 20px; background: $background; color: $foreground; font-family: sans-serif; line-height: 1.6; overflow-wrap: break-word; overflow-wrap: anywhere; }
            pre, code { font-family: monospace; }
            pre { margin: 0; white-space: ${if (wrap) "pre-wrap" else "pre"}; word-break: ${if (wrap) "break-all" else "normal"}; overflow-wrap: ${if (wrap) "anywhere" else "normal"}; tab-size: 4; line-height: 1.6; }
            pre.numbered { padding-left: 4.5em; }
            .line-marker { position: relative; }
            .line-marker::before { content: attr(data-line); position: absolute; right: 1em; width: 3.5em; text-align: right; color: ${if (dark) "#a6b3ab" else "#63766a"}; user-select: none; -webkit-user-select: none; }
            p, ul, ol, blockquote, table { margin: 0 0 1em; }
            h1, h2, h3 { line-height: 1.25; }
            blockquote { border-left: 3px solid #789486; padding-left: 16px; margin-left: 0; }
            table { border-collapse: collapse; display: block; overflow-x: auto; }
            th, td { border: 1px solid #789486; padding: 6px 12px; }
            .csv-scroll { overflow: auto; max-height: calc(100vh - 40px); }
            table.csv { display: table; overflow: visible; margin: 0; }
            .csv th { position: sticky; top: 0; background: $background; z-index: 1; }
            .csv th, .csv td { white-space: pre-wrap; min-width: 6em; max-width: 30em; overflow-wrap: anywhere; }
            a, .key { color: ${if (dark) "#9bcbb7" else "#245c50"}; }
            .string { color: ${if (dark) "#e4bd89" else "#86541d"}; }
            .number, .literal { color: ${if (dark) "#c2b2f4" else "#69429b"}; }
            .comment { color: ${if (dark) "#a6b3ab" else "#63766a"}; font-style: italic; }
            .addition { color: ${if (dark) "#9bcbb7" else "#245c50"}; }
            .deletion { color: ${if (dark) "#ffb4ab" else "#9a2925"}; }
            .calendar-value { white-space: pre-wrap; }
            mark { background: #ffe08a; color: #202923; }
            </style></head><body>""".trimIndent()
    }
}

/** Keeps the 5 MiB source intact without allocating its potentially 30 MiB HTML expansion. */
class HtmlPage(private val prefix: String, private val content: String, private val suffix: String, private val escape: Boolean, private val softBreaks: Boolean, private val numbered: Boolean = false, private val targetLine: Int = 0, private val match: SourceMatch? = null) {
    fun openStream(): java.io.InputStream = java.io.SequenceInputStream(java.util.Collections.enumeration(listOf(
        prefix.byteInputStream(Charsets.UTF_8), ChunkedTextStream(content, escape, softBreaks, numbered, targetLine, match), suffix.byteInputStream(Charsets.UTF_8)
    )))
}

private class ChunkedTextStream(private val text: String, private val escape: Boolean, private val softBreaks: Boolean, private val numbered: Boolean, private val targetLine: Int, private val match: SourceMatch?) : java.io.InputStream() {
    private var line = 1
    private var offset = 0
    private var chunk = java.io.ByteArrayInputStream(ByteArray(0))
    private var closed = false

    private fun availableChunk(): Boolean {
        if (closed) return false
        if (chunk.available() > 0) return true
        if (offset == text.length) return false
        var end = minOf(offset + 1024, text.length)
        if (end < text.length && Character.isHighSurrogate(text[end - 1])) end--
        val start = offset
        val part = text.substring(start, end)
        offset = end
        // Old WebViews can spend minutes laying out uninterrupted punctuation runs.
        // Soft breaks add no source characters and bound that work. Only insert at
        // ASCII boundaries, never inside CRLF, surrogate pairs, or combining text.
        val softBreak = softBreaks && end < text.length && text[end - 1] in ' '..'~' && text[end] in ' '..'~'
        val decorated = if (match != null && escape) buildString {
            fun marker() {
                if (numbered) append("<span aria-hidden=\"true\" class=\"line-marker\" data-line=\"$line\"></span>")
            }
            if (start == 0) marker()
            for (index in part.indices) {
                val absolute = start + index
                if (absolute == match.start) append("<mark id=\"source-match\">")
                when (val c = part[index]) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '"' -> append("&quot;")
                    '\'' -> append("&#39;")
                    else -> append(c)
                }
                if (absolute + 1 == match.end) append("</mark>")
                if (part[index] == '\n' || (part[index] == '\r' && text.getOrNull(absolute + 1) != '\n')) { line++; marker() }
            }
        } else if (!numbered && targetLine == 0) {
            if (escape) SyntaxHighlighter.escape(part) else part
        } else buildString {
            fun marker() {
                if (numbered || line == targetLine) append("<span aria-hidden=\"true\" id=\"line-$line\"" +
                    (if (numbered) " class=\"line-marker\" data-line=\"$line\"" else "") + "></span>")
            }
            if (start == 0) marker()
            var segment = 0
            for (index in part.indices) {
                val absolute = start + index
                if (part[index] == '\n' || (part[index] == '\r' && text.getOrNull(absolute + 1) != '\n')) {
                    val value = part.substring(segment, index + 1)
                    append(if (escape) SyntaxHighlighter.escape(value) else value)
                    line++
                    marker()
                    segment = index + 1
                }
            }
            val value = part.substring(segment)
            append(if (escape) SyntaxHighlighter.escape(value) else value)
        }
        chunk = (decorated + if (softBreak) "<wbr>" else "").byteInputStream(Charsets.UTF_8)
        return true
    }

    override fun read(): Int = if (availableChunk()) chunk.read() else -1
    override fun available(): Int = if (availableChunk()) chunk.available() else 0
    override fun read(buffer: ByteArray, off: Int, len: Int): Int {
        if (off < 0 || len < 0 || off > buffer.size - len) throw IndexOutOfBoundsException()
        if (len == 0) return 0
        return if (availableChunk()) chunk.read(buffer, off, len) else -1
    }
    override fun close() { closed = true; chunk.close() }
}
