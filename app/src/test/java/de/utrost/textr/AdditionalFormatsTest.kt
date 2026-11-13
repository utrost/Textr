package de.utrost.textr

import org.junit.Assert.*
import org.junit.Test

class AdditionalFormatsTest {
    private fun prepared(text: String, kind: FileKind) = DocumentRenderer.prepare(TextDocument("file", text, kind, text.toByteArray().size))
    private fun calendar(event: String) = "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nBEGIN:VEVENT\r\n$event\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n"

    @Test fun `extensions and MIME aliases agree with source precedence`() {
        val formats = mapOf("JAVA" to FileKind.JAVA, "js" to FileKind.JAVASCRIPT, "mjs" to FileKind.JAVASCRIPT,
            "cjs" to FileKind.JAVASCRIPT, "CSS" to FileKind.CSS, "htm" to FileKind.HTML, "html" to FileKind.HTML,
            "kt" to FileKind.KOTLIN, "kts" to FileKind.KOTLIN, "py" to FileKind.PYTHON, "sh" to FileKind.SHELL,
            "bash" to FileKind.SHELL, "sql" to FileKind.SQL, "patch" to FileKind.DIFF, "diff" to FileKind.DIFF,
            "ICS" to FileKind.ICALENDAR, "ical" to FileKind.ICALENDAR)
        formats.forEach { (extension, kind) -> assertEquals(extension, kind, TextDocuments.kind("file.$extension", "text/plain")) }
        for (mime in listOf("text/javascript", "application/javascript", "application/x-javascript", "application/ecmascript"))
            assertEquals(FileKind.JAVASCRIPT, TextDocuments.kind("unknown", mime))
        assertEquals(FileKind.ICALENDAR, TextDocuments.kind("unknown", "TEXT/CALENDAR; charset=utf-8"))
        assertEquals(FileKind.JAVA, TextDocuments.kind("unknown", "application/x-java-source"))
        assertEquals(FileKind.HTML, TextDocuments.kind("unknown", "application/xhtml+xml"))
        assertEquals(FileKind.TEXT, TextDocuments.kind("file.txt", "text/calendar"))
        assertEquals(FileKind.TEXT, TextDocuments.kind("file.ja", null))
    }

    @Test fun `new highlighting preserves exact hostile Unicode source and line endings`() {
        val source = "/* <script>bad</script> */\r\nconst name = `🚀 & stuff`;\r# comment\n'quote\\\"' "+"\"unterminated"
        for (kind in SourceHighlighter.kinds + FileKind.HTML) {
            val output = SyntaxHighlighter.highlight(source, kind)
            assertEquals(kind.name, SyntaxHighlighter.escape(source), output.replace(Regex("</?span[^>]*>"), ""))
            assertFalse(output.contains("<script>"))
            assertTrue(DisplayMode.HIGHLIGHTED in prepared(source, kind).modes)
            if (kind != FileKind.ICALENDAR) assertNull(prepared(source, kind).preview)
        }
    }

    @Test fun `language specific tokens and multiline strings receive decoration`() {
        val examples = mapOf(FileKind.JAVA to "public class Test { String a = \"\"\"text\nblock\"\"\"; /* comment */ }",
            FileKind.JAVASCRIPT to "const value = `a\nb`; // comment",
            FileKind.KOTLIN to "val value = \"\"\"a\nb\"\"\" // comment",
            FileKind.PYTHON to "def example():\n  return '''a\nb''' # comment",
            FileKind.SHELL to "if true; then echo 'hello'; fi # comment",
            FileKind.SQL to "SELECT 'it''s text' FROM data -- comment")
        examples.forEach { (kind, source) ->
            val result = SyntaxHighlighter.highlight(source, kind)
            for (style in listOf("literal", "string", "comment")) assertTrue("$kind $style", result.contains("class=\"$style\""))
            assertEquals(SyntaxHighlighter.escape(source), result.replace(Regex("</?span[^>]*>"), ""))
        }
        val css = SyntaxHighlighter.highlight(".box { color: #fff; content: 'x'; /* note */ }", FileKind.CSS)
        for (style in listOf("key", "number", "string", "comment")) assertTrue(css.contains("class=\"$style\""))
        val diff = SyntaxHighlighter.highlight("--- a\n+++ b\n@@ -1 +1 @@\n-old\n+new\n", FileKind.DIFF)
        assertTrue(diff.contains("class=\"addition\">+new")); assertTrue(diff.contains("class=\"deletion\">-old"))
    }

    @Test fun `new lexers keep token and input budgets without source truncation`() {
        val cases = mapOf(FileKind.JAVASCRIPT to "const a = 'x';\n", FileKind.CSS to "p{color:red;}\n",
            FileKind.DIFF to "+new\n", FileKind.ICALENDAR to "SUMMARY:value\n")
        cases.forEach { (kind, fragment) ->
            val source = fragment.repeat(12_000)
            val output = SyntaxHighlighter.highlight(source, kind)
            assertTrue(Regex("<span").findAll(output).count() <= RenderPolicy.MAX_HIGHLIGHT_TOKENS)
            assertEquals(SyntaxHighlighter.escape(source), output.replace(Regex("</?span[^>]*>"), ""))
        }
        val big = "a".repeat(RenderPolicy.MAX_HIGHLIGHT_CHARS + 1)
        assertEquals(listOf(DisplayMode.PLAIN), prepared(big, FileKind.JAVA).modes)
        assertEquals(big, SyntaxHighlighter.highlight(big, FileKind.JAVA))
    }

    @Test fun `calendar unfolds and escapes fields without allowing active HTML`() {
        val source = calendar("SUMMARY:Meeting\\, part 1\r\n continued\r\nDTSTART:20261113T100000Z\r\nLOCATION:A\\;B\r\nDESCRIPTION:Line one\\n<script>alert(1)</script>\\n<img src=https://evil.test/a>\r\nURL:https://evil.test")
        val doc = prepared(source, FileKind.ICALENDAR)
        assertEquals(DisplayMode.PREVIEW, doc.defaultMode)
        val html = doc.preview!!
        for (value in listOf("Meeting, part 1continued", "2026-11-13 10:00:00 (UTC)", "A;B", "Line one\n&lt;script&gt;")) assertTrue(value, html.contains(value))
        assertFalse(html.contains("<script>")); assertFalse(html.contains("<img")); assertFalse(html.contains("href="))
        assertEquals(source, doc.source.text)
        assertEquals(SyntaxHighlighter.escape(source), SyntaxHighlighter.highlight(source, FileKind.ICALENDAR).replace(Regex("</?span[^>]*>"), ""))
    }

    @Test fun `calendar distinguishes all day exclusive end floating UTC and supplied zones`() {
        val allDay = CalendarPreview.render(calendar("DTSTART;VALUE=DATE:20260228\r\nDTEND;VALUE=DATE:20260302"))
        assertTrue(allDay.contains("2026-02-28 (all day)")); assertTrue(allDay.contains("End (exclusive)"))
        assertTrue(allDay.contains("2026-03-02 (all day)"))
        assertTrue(CalendarPreview.render(calendar("DTSTART:20261113T120000")).contains("floating time; no time zone supplied"))
        val zoned = CalendarPreview.render(calendar("DTSTART;TZID=\"Custom:Zone;Part\":20261113T120000"))
        assertTrue(zoned.contains("12:00:00 (time zone: Custom:Zone;Part)"))
        assertTrue(CalendarPreview.render(calendar("DTSTART;TZID=Zone^^Name:20261113T120000")).contains("Zone^Name"))
        assertTrue(CalendarPreview.render(calendar("DTSTART;TZID=Zone^NName:20261113T120000")).contains("Zone^NName"))
        assertTrue(CalendarPreview.render(calendar("DTSTART;VALUE=DATE-TIME:20261113T120000Z\r\nDTEND:20261113T130000Z")).contains("13:00:00 (UTC)"))
    }

    @Test fun `calendar shows multiple events recurrence exceptions and ignores alarm fields`() {
        val event = "SUMMARY:First\r\nDTSTART:20261113T120000Z\r\nRRULE:FREQ=DAILY;COUNT=3\r\nEXDATE;TZID=Europe/Berlin:20261114T130000\r\nRDATE:20261120T120000Z\r\nBEGIN:VALARM\r\nDESCRIPTION:Alarm text\r\nSUMMARY:Alarm title\r\nEND:VALARM"
        val source = calendar(event).replace("END:VCALENDAR", "BEGIN:VEVENT\r\nSUMMARY:Changed instance\r\nRECURRENCE-ID;RANGE=THISANDFUTURE:20261115T120000Z\r\nSTATUS:CANCELLED\r\nEND:VEVENT\r\nEND:VCALENDAR")
        val html = CalendarPreview.render(source)
        assertEquals(2, Regex("<h2>").findAll(html).count())
        for (value in listOf("FREQ=DAILY;COUNT=3", "TZID=Europe/Berlin", "Recurrence instance", "THISANDFUTURE", "CANCELLED")) assertTrue(html.contains(value))
        assertFalse(html.contains("Alarm text")); assertFalse(html.contains("Alarm title"))
    }

    @Test fun `malformed unsupported and ambiguous calendars fall back to complete source`() {
        val badEvents = listOf("DTSTART:20260230T120000Z", "DTSTART;VALUE=DATE:20250229", "DTSTART;TZID=Europe/Berlin:20261113T120000Z",
            "SUMMARY:One\r\nSUMMARY:Two", "DTSTART:20261113T120000Z\r\nDTEND:20261112T120000Z",
            "DTSTART;VALUE=DATE:20261113\r\nDTEND:20261114T120000Z", "DTEND:20261113T120000Z\r\nDURATION:P1D",
            "DTSTART;VALUE=DATE;VALUE=DATE:20261113", "DTSTART;TZID=\"unclosed:20261113T120000", "BROKEN")
        val sources = badEvents.map(::calendar) + listOf(calendar("SUMMARY:x").replace("END:VEVENT", "END:VTODO"),
            "BEGIN:VCALENDAR\nVERSION:2.0\nBEGIN:VTODO\nSUMMARY:Task\nEND:VTODO\nEND:VCALENDAR",
            calendar("SUMMARY:x").replace("VERSION:2.0", "VERSION:1.0"))
        sources.forEach { source ->
            val result = prepared(source, FileKind.ICALENDAR)
            assertNull(source, result.preview); assertEquals(PreviewNotice.UNAVAILABLE, result.notice)
            assertEquals(source, result.source.text)
        }
    }

    @Test fun `calendar budgets reject excess instead of showing an incomplete event list`() {
        val tooMany = "BEGIN:VCALENDAR\nVERSION:2.0\n" + "BEGIN:VEVENT\nSUMMARY:X\nEND:VEVENT\n".repeat(CalendarPreview.MAX_EVENTS + 1) + "END:VCALENDAR"
        val deep = calendar("BEGIN:X\n".repeat(CalendarPreview.MAX_DEPTH) + "END:X\n".repeat(CalendarPreview.MAX_DEPTH))
        val longLine = calendar("DESCRIPTION:" + "x".repeat(CalendarPreview.MAX_LINE))
        val properties = calendar("X-EXTRA:y\n".repeat(CalendarPreview.MAX_PROPERTIES))
        val expanded = "BEGIN:VCALENDAR\nVERSION:2.0\n" + ("BEGIN:VEVENT\nDESCRIPTION:" + "'".repeat(10_000) + "\nEND:VEVENT\n").repeat(45) + "END:VCALENDAR"
        assertTrue(expanded.length < CalendarPreview.MAX_CHARS)
        for (source in listOf(tooMany, deep, longLine, properties, expanded)) assertNull(prepared(source, FileKind.ICALENDAR).preview)
        val big = "x".repeat(CalendarPreview.MAX_CHARS + 1)
        val result = prepared(big, FileKind.ICALENDAR)
        assertEquals(PreviewNotice.CALENDAR_LIMIT, result.notice); assertEquals(big, result.source.text)
    }

    @Test fun `calendar and source rendering honor cancellation`() {
        Thread.currentThread().interrupt()
        try {
            try { CalendarPreview.render(calendar("SUMMARY:x")); fail("Expected cancellation") }
            catch (_: java.util.concurrent.CancellationException) { }
            try { SyntaxHighlighter.highlight("const x = 1", FileKind.JAVASCRIPT); fail("Expected cancellation") }
            catch (_: java.util.concurrent.CancellationException) { }
        } finally { Thread.interrupted() }
    }
}
