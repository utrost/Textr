package de.utrost.textr

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CancellationException

class HtmlMarkdownTest {
    @org.junit.Test fun exportRetainsTheActualConversionAndReopensAsMarkdownWithoutTouchingHtml() {
        val html = "<h1>Grüße 🚀</h1><ul><li>One</li></ul><pre><code>a &lt; b</code></pre><table><tr><th>A</th><th>B</th></tr><tr><td>1</td><td>2</td></tr></table><p><a href='https://example.invalid/'>Link</a><img src='https://example.invalid/a.png' alt='Figure'></p><script>never</script>"
        val source = TextDocuments.read(html.byteInputStream(), "saved.html")
        val prepared = DocumentRenderer.prepare(source)
        val converted = prepared.convertedMarkdown!!
        org.junit.Assert.assertEquals(HtmlMarkdown.convert(html), converted)
        val output = java.io.ByteArrayOutputStream()
        EncodedOutput.write(converted, TextEncoding.UTF8, false, output)
        val reopened = TextDocuments.read(output.toByteArray().inputStream(), "saved.md", "text/markdown")
        org.junit.Assert.assertEquals(converted, reopened.text)
        org.junit.Assert.assertEquals(FileKind.MARKDOWN, reopened.kind)
        val rendered = DocumentRenderer.prepare(reopened)
        org.junit.Assert.assertNotNull(rendered.preview)
        org.junit.Assert.assertEquals(listOf("Grüße 🚀"), rendered.headings.map { it.title })
        org.junit.Assert.assertTrue(rendered.preview!!.contains("<table>"))
        org.junit.Assert.assertFalse(converted.contains("<script>"))
        org.junit.Assert.assertEquals(html, source.text)
        org.junit.Assert.assertNull(DocumentRenderer.prepare(source.copy(text = "x".repeat(HtmlMarkdown.MAX_CHARS + 1))).convertedMarkdown)
    }

    private fun prepare(html: String) = DocumentRenderer.prepare(TextDocument("saved.html", html, FileKind.HTML, html.toByteArray().size))
    private fun preview(html: String) = prepare(html).preview!!

    @Test fun `HTML is converted through Markdown with headings lists quotes links and tables`() {
        val source = """<!doctype html><html><head><title>Not article content</title></head><body>
            <h1>Reading &amp; notes</h1><p>Hello <strong>bold</strong> and <em>italic</em>.</p>
            <ol start="3"><li>First<ul><li>Nested</li></ul></li><li>Second</li></ol>
            <blockquote><p>A quotation</p></blockquote><a href="https://example.com/a(b)?q=1&amp;x=2">Visit</a>
            <table><caption>Data</caption><tr><th>Name</th><th>Value</th></tr><tr><td>A</td><td>1</td></tr></table>
            </body></html>""".trimIndent()
        val markdown = HtmlMarkdown.convert(source)
        assertTrue(markdown.contains("# Reading &amp; notes"))
        val prepared = prepare(source)
        val html = prepared.preview!!
        for (tag in listOf("<h1 id=\"heading-1\">", "<strong>", "<em>", "<ol start=\"3\">", "<ul>", "<blockquote>", "<table>"))
            assertTrue("Missing $tag in $html", html.contains(tag))
        assertTrue(html.contains("https://example.com/a%28b%29?q=1&amp;x=2"))
        assertEquals(listOf(OutlineHeading("heading-1", "Reading & notes", 1)), prepared.headings)
        assertEquals(source, prepared.source.text)
        assertEquals(DisplayMode.HIGHLIGHTED, prepared.defaultMode)
        assertEquals(listOf(DisplayMode.PLAIN, DisplayMode.HIGHLIGHTED, DisplayMode.PREVIEW), prepared.modes)
    }

    @Test fun `scripts resources forms and unsafe links never reach the preview`() {
        val html = preview("""<base href="https://tracker.invalid/"><meta http-equiv="refresh" content="0;url=https://tracker.invalid">
            <style>body{background:url(https://tracker.invalid)}</style><script>EXECUTED</script>
            <iframe srcdoc="bad">Embedded</iframe><form>Secret<input value="secret"></form>
            <svg><script>svg-script</script></svg><p onclick="bad()">Readable</p>
            <img src="https://tracker.invalid/pixel" onerror="bad()"><p hidden>Hidden</p>
            <a href="javascript:alert(1)">Script link</a><a href="data:text/html,bad">Data link</a>
            <a href="file:///private">File link</a><a href="content://private">Content link</a>
            <a href="../relative">Relative link</a><a href="#old-id">Fragment</a>
            <a href="HTTPS://example.com/path">Safe link</a><a href="mailto:reader@example.com">Mail</a>""")
        for (omitted in listOf("<script", "<img", "<iframe", "<form", "<svg", "onclick", "onerror", "tracker.invalid", "EXECUTED", "Secret", "Hidden", "href=\"javascript:", "href=\"data:", "href=\"file:", "href=\"content:", "href=\"../", "href=\"#"))
            assertFalse("Unexpected $omitted in $html", html.contains(omitted))
        for (text in listOf("Readable", "Script link", "Relative link", "Safe link", "mailto:reader@example.com", "[image omitted]"))
            assertTrue(text, html.contains(text))
    }

    @Test fun `literal Markdown HTML and entities cannot become active markup`() {
        val html = preview("<p># Literal *stars* [label](https://injected.invalid) ![image](https://pixel.invalid) &lt;img src=x&gt; &amp;lt; 🚀 café</p>")
        assertFalse(html.contains("<h1")); assertFalse(html.contains("<em>")); assertFalse(html.contains("<a ")); assertFalse(html.contains("<img"))
        assertTrue(html.contains("&lt;img src=x&gt;")); assertTrue(html.contains("&amp;lt;")); assertTrue(html.contains("🚀 café"))
    }

    @Test fun `code fences whitespace and pipes survive conversion without breaking structure`() {
        val html = preview("<pre><code>&lt;script&gt;literal&lt;/script&gt;\n```\n\nkeep blank line\n</code></pre><p><code>` edge `</code> <strong> spaced </strong>tail</p>")
        assertTrue(html.contains("&lt;script&gt;literal&lt;/script&gt;\n```\n\nkeep blank line"))
        assertTrue(html.contains("<code>` edge `</code>"))
        assertTrue(html.contains(" <strong>spaced</strong> tail"))
        val table = preview("<table><tr><th>Code</th></tr><tr><td><code>a|b</code> | text</td></tr></table>")
        assertTrue(table.contains("<code>a|b</code> | text"))
        assertEquals(1, Regex("<td>").findAll(table).count())
    }

    @Test fun `malformed HTML and XHTML are readable without requiring valid XML`() {
        val html = preview("<html xmlns='http://www.w3.org/1999/xhtml'><body><h2>Title</h2><p>One<br/>Two<p>Three &copy;")
        assertTrue(html.contains("<h2")); assertTrue(html.contains("One<br />\nTwo")); assertTrue(html.contains("Three ©"))
    }

    @Test fun `headerless tables retain the first data row and complex tables fall back`() {
        val html = preview("<table><tr><td>First</td><td>1</td></tr><tr><td>Second</td><td>2</td></tr></table>")
        assertTrue(html.contains("<td>First</td>")); assertEquals(4, Regex("<td>").findAll(html).count())
        for (source in listOf("<table><tr><td colspan=2>Wide</td></tr></table>",
            "<table><tr><td>A</td><td>B</td></tr><tr><td>C</td></tr></table>",
            "<table><tr><td><table><tr><td>Nested</td></tr></table></td></tr></table>")) {
            assertNull(prepare(source).preview); assertEquals(source, prepare(source).source.text)
        }
    }

    @Test fun `HTML and generated Markdown budgets preserve complete source on fallback`() {
        val exact = "x".repeat(HtmlMarkdown.MAX_CHARS)
        assertNotNull(prepare(exact).preview)
        val over = exact + "x"
        assertEquals(PreviewNotice.HTML_LIMIT, prepare(over).notice)
        assertNull(prepare(over).preview); assertEquals(over, prepare(over).source.text)
        // Escape expansion exceeds the Markdown budget even though the HTML fits.
        val expansion = "&".repeat(150_000)
        assertNull(prepare(expansion).preview); assertEquals(expansion, prepare(expansion).source.text)
    }

    @Test fun `DOM depth node and table budgets fail closed to original source`() {
        for (source in listOf("<div>".repeat(HtmlMarkdown.MAX_DEPTH + 1) + "deep" + "</div>".repeat(HtmlMarkdown.MAX_DEPTH + 1),
            "<b>x</b>".repeat(11_000),
            "<table>" + "<tr><td>x</td></tr>".repeat(101) + "</table>")) {
            val prepared = prepare(source)
            assertNull(prepared.preview); assertEquals(source, prepared.source.text)
        }
    }

    @Test fun `empty unreadable content falls back and image omission is localized`() {
        assertNull(prepare("<script>bad()</script>").preview)
        val doc = TextDocument("test.htm", "<img src='https://unused.invalid'><p>Original</p>", FileKind.HTML, 60)
        val html = DocumentRenderer.prepare(doc, translate = { if (it == "[image omitted]") "[Bild ausgelassen]" else it }).preview!!
        assertTrue(html.contains("[Bild ausgelassen]")); assertTrue(html.contains("Original")); assertFalse(html.contains("unused.invalid"))
    }


    @Test fun `cancellation during conversion propagates through the renderer`() {
        val source = TextDocument("cancel.html", "<p>Before</p><img><p>After</p>", FileKind.HTML, 40)
        try {
            try {
                DocumentRenderer.prepare(source, translate = { Thread.currentThread().interrupt(); it })
                fail("Expected cancellation during image conversion")
            } catch (_: CancellationException) { }
        } finally { Thread.interrupted() }
    }

    @Test fun `cancellation never returns a partially converted page`() {
        Thread.currentThread().interrupt()
        try {
            try { HtmlMarkdown.convert("<p>Never published</p>"); fail("Expected cancellation") }
            catch (_: CancellationException) { }
        } finally { Thread.interrupted() }
    }
}
