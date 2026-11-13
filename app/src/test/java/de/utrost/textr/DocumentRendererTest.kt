package de.utrost.textr

import org.junit.Assert.*
import org.junit.Test

class DocumentRendererTest {
    @Test fun `streamed HTML preserves Unicode across chunk boundaries and escapes source`() {
        val source = "a".repeat(8191) + "\uD83D\uDE80<&>\"'" + "&".repeat(20_000)
        val page = DocumentRenderer.page(prepare(source, FileKind.TEXT), DisplayMode.PLAIN, false, true)
        val first = page.openStream().bufferedReader(Charsets.UTF_8).use { it.readText() }
        assertTrue(first.replace("<wbr>", "").contains("<pre>${SyntaxHighlighter.escape(source)}</pre>"))
        assertEquals(first, page.openStream().bufferedReader(Charsets.UTF_8).use { it.readText() })
    }
    @org.junit.Test fun largeJsonKeepsSourceWithoutExpandingItsPreview() {
        val text = "[" + "0,".repeat(600_000) + "1]"
        val source = TextDocument("large.json", text, FileKind.JSON, text.length)
        val prepared = DocumentRenderer.prepare(source)
        org.junit.Assert.assertNull(prepared.preview)
        org.junit.Assert.assertEquals(PreviewNotice.JSON_LIMIT, prepared.notice)
        org.junit.Assert.assertEquals(text, prepared.source.text)
        org.junit.Assert.assertTrue(DisplayMode.PLAIN in prepared.modes)
    }

    @Test fun `heading outline has unique safe anchors and readable labels`() {
        val result = prepare("# Same\n\n## Same\n\n### \n\n# A `code` **label**", FileKind.MARKDOWN)
        assertEquals(listOf("Same", "Same", "Untitled heading", "A code label"), result.headings.map { it.title })
        assertEquals(listOf(1, 2, 3, 1), result.headings.map { it.level })
        assertEquals(4, result.headings.map { it.id }.distinct().size)
        result.headings.forEach { assertTrue(result.preview!!.contains("id=\"${it.id}\"")) }
    }

    @Test fun `source anchors preserve line endings unicode and highlighted multiline tokens`() {
        val text = "<!-- first\r\nsecond\rthird -->\n" + "x".repeat(1020) + "🚀\nlast"
        for (mode in listOf(DisplayMode.PLAIN, DisplayMode.HIGHLIGHTED)) {
            val html = DocumentRenderer.page(prepare(text, FileKind.XML), mode, false, true, true, 4)
                .openStream().bufferedReader().use { it.readText() }
            assertEquals(5, DocumentRenderer.logicalLines(text))
            assertEquals(5, Regex("id=\"line-").findAll(html).count())
            val source = html.substringAfter("<pre class=\"numbered\">").substringBefore("</pre>")
                .replace(Regex("</?span[^>]*>"), "").replace("<wbr>", "")
            assertEquals(SyntaxHighlighter.escape(text), source)
        }
    }

    @Test fun `large source uses one jump anchor and bounded line decorations`() {
        val text = "line\n".repeat(100_000)
        val html = DocumentRenderer.page(prepare(text, FileKind.TEXT), DisplayMode.PLAIN, false, true, true, 90_000)
            .openStream().bufferedReader().use { it.readText() }
        assertFalse(html.contains("<pre class=\"numbered\">"))
        assertEquals(1, Regex("id=\"line-").findAll(html).count())
        assertTrue(html.contains("id=\"line-90000\""))
    }

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

    @Test fun `oversized and excessively nested Markdown retains complete source`() {
        val source = "a".repeat(RenderPolicy.MAX_MARKDOWN_CHARS + 1)
        val prepared = prepare(source, FileKind.MARKDOWN)
        assertNull(prepared.preview)
        assertEquals(PreviewNotice.MARKDOWN_LIMIT, prepared.notice)
        assertEquals(source, prepared.source.text)
        val nested = prepare("> ".repeat(100) + "deep", FileKind.MARKDOWN)
        assertNull(nested.preview)
        assertTrue(nested.source.text.endsWith("deep"))
    }

    @Test fun `cancelled rendering stops rather than producing a stale page`() {
        Thread.currentThread().interrupt()
        try {
            try {
                DocumentRenderer.prepare(TextDocument("test.md", "# Heading", FileKind.MARKDOWN, 9))
                fail("Expected cancellation")
            } catch (_: java.util.concurrent.CancellationException) { }
        } finally { Thread.interrupted() }
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
        for (tag in listOf("<h1 id=\"heading-1\">", "<blockquote>", "<ul>", "<table>", "<pre>")) assertTrue(tag, result.contains(tag))
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
        assertTrue(Regex("<span").findAll(html).count() <= 10_000)
        val longString = "\"" + "\\x".repeat(100_000) + "\""
        assertTrue(SyntaxHighlighter.highlight(longString, FileKind.JSON).endsWith("</span>"))
    }
}
