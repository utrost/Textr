package de.utrost.textr

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.core.StreamReadConstraints
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.HtmlBlock
import org.commonmark.node.HtmlInline
import org.commonmark.node.Image
import org.commonmark.node.Text
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import java.io.StringWriter

data class PreparedDocument(val source: TextDocument, val preview: String?) {
    val modes: List<DisplayMode> get() = buildList {
        add(DisplayMode.PLAIN)
        if (source.kind != FileKind.TEXT) add(DisplayMode.HIGHLIGHTED)
        if (preview != null) add(DisplayMode.PREVIEW)
    }
    val defaultMode: DisplayMode get() = modes.last()
}

object DocumentRenderer {
    private val json = JsonFactory.builder().streamReadConstraints(
        StreamReadConstraints.builder().maxNestingDepth(128)
            .maxStringLength(MAX_FILE_BYTES).maxNumberLength(MAX_FILE_BYTES).build()
    ).build()

    fun prepare(document: TextDocument): PreparedDocument {
        val preview = try {
            when (document.kind) {
                FileKind.JSON -> prettyJson(document.text)?.let { "<pre>${SyntaxHighlighter.escape(it)}</pre>" }
                FileKind.MARKDOWN -> markdown(document.text)
                else -> null
            }
        } catch (_: StackOverflowError) {
            // Deeply nested Markdown may exceed the renderer's stack; source is still usable.
            null
        } catch (_: IllegalArgumentException) {
            null
        }
        return PreparedDocument(document, preview)
    }

    fun prettyJson(text: String): String? = try {
        json.createParser(text).use { parser ->
            if (parser.nextToken() == null) return null
            val output = object : StringWriter() {
                override fun write(str: String, off: Int, len: Int) {
                    require(buffer.length + len <= MAX_FILE_BYTES * 4)
                    super.write(str, off, len)
                }
                override fun write(cbuf: CharArray, off: Int, len: Int) {
                    require(buffer.length + len <= MAX_FILE_BYTES * 4)
                    super.write(cbuf, off, len)
                }
            }
            json.createGenerator(output).use { generator ->
                generator.useDefaultPrettyPrinter()
                var depth = 0
                do {
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

    fun markdown(source: String): String {
        val extensions = listOf(TablesExtension.create())
        val node = Parser.builder().extensions(extensions).build().parse(source)
        node.accept(object : AbstractVisitor() {
            override fun visit(image: Image) {
                image.insertBefore(Text("[image omitted]"))
                image.unlink()
            }
            override fun visit(htmlBlock: HtmlBlock) { htmlBlock.unlink() }
            override fun visit(htmlInline: HtmlInline) { htmlInline.unlink() }
        })
        return HtmlRenderer.builder().extensions(extensions).escapeHtml(true)
            .sanitizeUrls(true).build().render(node)
    }

    fun html(document: PreparedDocument, mode: DisplayMode, dark: Boolean, wrap: Boolean): String {
        val content = when (mode) {
            DisplayMode.PREVIEW -> document.preview ?: "<pre>${SyntaxHighlighter.escape(document.source.text)}</pre>"
            DisplayMode.HIGHLIGHTED -> "<pre>${SyntaxHighlighter.highlight(document.source.text, document.source.kind)}</pre>"
            DisplayMode.PLAIN -> "<pre>${SyntaxHighlighter.escape(document.source.text)}</pre>"
        }
        val background = if (dark) "#171c1a" else "#fafaf7"
        val foreground = if (dark) "#e3e9e5" else "#202923"
        return """<!doctype html><html><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'; img-src 'none'; script-src 'none'; base-uri 'none'; form-action 'none'">
            <style>
            :root { color-scheme: ${if (dark) "dark" else "light"}; }
            body { margin: 0; padding: 20px; background: $background; color: $foreground; font-family: sans-serif; line-height: 1.6; overflow-wrap: anywhere; }
            pre, code { font-family: monospace; }
            pre { margin: 0; white-space: ${if (wrap) "pre-wrap" else "pre"}; overflow-wrap: ${if (wrap) "anywhere" else "normal"}; tab-size: 4; line-height: 1.6; }
            p, ul, ol, blockquote, table { margin: 0 0 1em; }
            h1, h2, h3 { line-height: 1.25; }
            blockquote { border-left: 3px solid #789486; padding-left: 16px; margin-left: 0; }
            table { border-collapse: collapse; display: block; overflow-x: auto; }
            th, td { border: 1px solid #789486; padding: 6px 12px; }
            a, .key { color: ${if (dark) "#9bcbb7" else "#245c50"}; }
            .string { color: ${if (dark) "#e4bd89" else "#86541d"}; }
            .number, .literal { color: ${if (dark) "#c2b2f4" else "#69429b"}; }
            .comment { color: ${if (dark) "#a6b3ab" else "#63766a"}; font-style: italic; }
            </style></head><body>$content</body></html>""".trimIndent()
    }
}
