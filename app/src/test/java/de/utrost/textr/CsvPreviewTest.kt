package de.utrost.textr

import org.junit.Assert.*
import org.junit.Test

class CsvPreviewTest {
    @Test fun quotedFieldsEscapedQuotesAndLineEndingsRemainExact() {
        assertEquals(listOf(listOf("a,b", "say \"hi\"", "line\r\nbreak"), listOf("", "", "last")),
            CsvPreview.parse("\"a,b\",\"say \"\"hi\"\"\",\"line\r\nbreak\"\r\n,,last\r\n"))
        assertEquals(listOf(listOf("a", "b"), listOf("c", "d")), CsvPreview.parse("a;b\rc;d", ';'))
        assertEquals(listOf(listOf("a", "b")), CsvPreview.parse("a\tb", '\t'))
        assertEquals(listOf(listOf("a", "b")), CsvPreview.parse("a|b", '|'))
    }

    @Test fun emptyTrailingAndRaggedFieldsAreNotDiscardedOrPadded() {
        assertEquals(emptyList<List<String>>(), CsvPreview.parse(""))
        assertEquals(listOf(listOf("")), CsvPreview.parse("\"\""))
        assertEquals(listOf(listOf("", "")), CsvPreview.parse(","))
        assertEquals(listOf(listOf(""), listOf("")), CsvPreview.parse("\n\n"))
        assertEquals(listOf(listOf("a", "b"), listOf("c"), listOf("d", "", "")), CsvPreview.parse("a,b\nc\nd,,"))
    }

    @Test fun malformedQuotingFallsBackWithoutLosingSource() {
        for (text in listOf("\"unterminated", "a\"b,c", "\"a\"oops,b", "\"a\" ,b")) {
            val prepared = prepare(text)
            assertNull(text, prepared.preview)
            assertEquals(PreviewNotice.CSV_UNAVAILABLE, prepared.notice)
            assertEquals(listOf(DisplayMode.PLAIN), prepared.modes)
            assertEquals(text, prepared.source.text)
        }
    }

    @Test fun cellRowColumnFieldAndInputBudgetsRejectWholePreview() {
        val inputs = listOf("x".repeat(CsvPreview.MAX_CHARS + 1), "x".repeat(CsvPreview.MAX_FIELD + 1),
            "x\n".repeat(CsvPreview.MAX_ROWS + 1), List(CsvPreview.MAX_COLUMNS + 1) { "x" }.joinToString(","),
            (List(100) { "x" }.joinToString(",") + "\n").repeat(101))
        inputs.forEach { assertNull(prepare(it).preview) }
        assertEquals(1000, CsvPreview.parse("x\n".repeat(1000)).size)
        assertEquals(100, CsvPreview.parse(List(100) { "x" }.joinToString(",")).first().size)
        assertNotNull(prepare("x".repeat(CsvPreview.MAX_FIELD)).preview)
    }

    @Test fun headersAreExplicitAndHostileCellsRemainInert() {
        val text = "Name,Value\n<script>evil()</script>,=SUM(A1)"
        val default = CsvPreview.render(text, CsvOptions())
        assertTrue(default.contains("<th scope=\"col\">Column 1</th>"))
        assertTrue(default.contains("<td>Name</td>"))
        assertTrue(default.contains("&lt;script&gt;evil()&lt;/script&gt;"))
        assertTrue(default.contains("<td>=SUM(A1)</td>"))
        assertFalse(default.contains("<script>"))
        val header = CsvPreview.render(text, CsvOptions(firstRowHeader = true))
        assertTrue(header.contains("<th scope=\"col\">Name</th>"))
        assertFalse(header.contains("<td>Name</td>"))
    }

    @Test fun csvPreviewIsOptionalAndSourceAndSharingMetadataArePreserved() {
        val prepared = prepare("a;b\r\n1;2", CsvOptions(';', true))
        assertEquals(DisplayMode.PLAIN, prepared.defaultMode)
        assertEquals(listOf(DisplayMode.PLAIN, DisplayMode.PREVIEW), prepared.modes)
        assertTrue(prepared.preview!!.contains("<th scope=\"col\">a</th>"))
        assertTrue(DocumentRenderer.html(prepared, DisplayMode.PLAIN, false, true).contains("a;b\r\n1;2"))
        assertEquals(FileKind.CSV, TextDocuments.kind("data.CSV", null))
        assertEquals(FileKind.CSV, TextDocuments.kind("file", "text/csv"))
        assertEquals(FileKind.TEXT, TextDocuments.kind("file.txt", "text/csv"))
    }

    @Test fun parsingCanBeCancelled() {
        Thread.currentThread().interrupt()
        try {
            assertThrows(java.util.concurrent.CancellationException::class.java) { CsvPreview.parse("a,b") }
        } finally { Thread.interrupted() }
    }

    private fun prepare(text: String, options: CsvOptions = CsvOptions()) =
        DocumentRenderer.prepare(TextDocument("data.csv", text, FileKind.CSV, text.toByteArray().size), options)
}
