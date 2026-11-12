package de.utrost.textr

import org.junit.Assert.*
import org.junit.Test

class DocumentRendererTest {
    private fun prepare(text: String, kind: FileKind) = DocumentRenderer.prepare(TextDocument("file", text, kind, text.toByteArray().size))

    @Test fun `automatic modes and manual alternatives depend on the document`() {
        assertEquals(listOf(DisplayMode.PLAIN), prepare("hi", FileKind.TEXT).modes)
        assertEquals(DisplayMode.HIGHLIGHTED, prepare("x: true", FileKind.YAML).defaultMode)
        val json = prepare("{\"answer\":42}", FileKind.JSON)
        assertEquals(DisplayMode.entries.toList(), json.modes)
        assertEquals(DisplayMode.PREVIEW, json.defaultMode)
        val broken = prepare("{broken", FileKind.JSON)
        assertNull(broken.preview)
        assertEquals(DisplayMode.HIGHLIGHTED, broken.defaultMode)
    }

    @Test fun `JSON formatter rejects nonstandard JSON and trailing documents`() {
        for (text in listOf("", "{bad}", "{\"a\":1,}", "[1,]", "{\"a\":NaN}", "{} {}", "true false", "/*comment*/{}", "[1", "01")) {
            assertNull(text, DocumentRenderer.prettyJson(text))
        }
        for (text in listOf("null", "true", "\"string\"", "42", "[]", "{}")) assertNotNull(text, DocumentRenderer.prettyJson(text))
    }

    @Test fun `JSON preserves numeric precision spelling key order and duplicate keys`() {
        val source = "{\"x\":123456789012345678901234567890,\"x\":1.234567890123456789e+30,\"b\":1e-7}"
        val result = DocumentRenderer.prettyJson(source)!!
        assertTrue(result.contains("123456789012345678901234567890"))
        assertTrue(result.contains("1.234567890123456789e+30"))
        assertTrue(result.contains("1e-7"))
        assertEquals(2, Regex("\"x\"").findAll(result).count())
        assertEquals(source, prepare(source, FileKind.JSON).source.text)
    }

    @Test fun `deep JSON falls back to readable source`() {
        val source = "[".repeat(150) + "0" + "]".repeat(150)
        val prepared = prepare(source, FileKind.JSON)
        assertNull(prepared.preview)
        assertEquals(source, prepared.source.text)
    }

    @Test fun `Markdown renders supported structures but excludes images and embedded HTML`() {
        val markdown = """
            # Heading

            > Quote

            - Item

            [Link](https://example.com)

            ```json
            {"x": 1}
            ```

            | Name | Value |
            | --- | --- |
            | A | 1 |

            ![tracking](https://example.com/pixel.png)

            <script>alert('bad')</script>

            <img src="file:///private/data">
        """.trimIndent()
        val result = DocumentRenderer.markdown(markdown)
        for (tag in listOf("<h1>", "<blockquote>", "<ul>", "<table>", "<pre>")) assertTrue(tag, result.contains(tag))
        assertTrue(result.contains("href=\"https://example.com\""))
        assertFalse(result.contains("<img"))
        assertFalse(result.contains("<script"))
        assertFalse(result.contains("pixel.png"))
    }

    @Test fun `plain and highlighted source cannot inject HTML`() {
        val source = "<script>alert('x')</script> & \"quotes\""
        for (kind in FileKind.entries) {
            val highlighted = SyntaxHighlighter.highlight(source, kind)
            assertFalse(highlighted.contains("<script>"))
            assertTrue(highlighted.contains("&lt;"))
            assertTrue(highlighted.contains("&amp;"))
        }
        val html = DocumentRenderer.html(prepare(source, FileKind.TEXT), DisplayMode.PLAIN, false, true)
        assertTrue(html.contains("script-src 'none'"))
        assertTrue(html.contains("&lt;script&gt;"))
    }

    @Test fun `highlighting is bounded and preserves every source character`() {
        val source = "x: 1\n".repeat(30_000)
        val html = SyntaxHighlighter.highlight(source, FileKind.YAML)
        assertEquals(source, html.replace(Regex("</?span[^>]*>"), ""))
        assertTrue(Regex("<span").findAll(html).count() <= 50_000)
        val longString = "\"" + "\\x".repeat(100_000) + "\""
        assertTrue(SyntaxHighlighter.highlight(longString, FileKind.JSON).endsWith("</span>"))
    }
}
