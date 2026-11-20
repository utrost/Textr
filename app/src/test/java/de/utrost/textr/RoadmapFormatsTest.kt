package de.utrost.textr

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CancellationException

class RoadmapFormatsTest {
    @Test fun filenamesBeatMimeAndSpecialNamesDoNotOverrideKnownExtensions() {
        for ((name, kind) in listOf(".env" to FileKind.ENV, ".env.local" to FileKind.ENV,
            ".env.example" to FileKind.ENV, "app.env" to FileKind.ENV, "settings.PROPERTIES" to FileKind.PROPERTIES,
            "Dockerfile" to FileKind.DOCKERFILE, "Dockerfile.dev" to FileKind.DOCKERFILE,
            "build.Dockerfile" to FileKind.DOCKERFILE, "Dockerfile.md" to FileKind.MARKDOWN,
            "Dockerfile.txt" to FileKind.TEXT, ".env.json" to FileKind.JSON, ".env.local.txt" to FileKind.TEXT,
            "file.ts" to FileKind.TYPESCRIPT, "component.TSX" to FileKind.TSX, "table.tsv" to FileKind.CSV)) {
            assertEquals(name, kind, TextDocuments.kind(name, "application/xml"))
        }
        for ((mime, kind) in listOf("text/x-env" to FileKind.ENV, "application/x-java-properties" to FileKind.PROPERTIES,
            "text/x-dockerfile" to FileKind.DOCKERFILE, "application/typescript" to FileKind.TYPESCRIPT,
            "text/x-tsx" to FileKind.TSX, "text/tab-separated-values" to FileKind.CSV, "text/tsv" to FileKind.CSV)) {
            assertEquals(kind, TextDocuments.kind("unknown", mime))
            assertEquals(FileKind.TEXT, TextDocuments.kind("known.txt", mime))
        }
        assertEquals(FileKind.TEXT, TextDocuments.kind("unknown", "video/mp2t"))
        assertEquals(FileKind.TEXT, TextDocuments.kind("myDockerfile", null))
    }
    @Test fun tsvDefaultsRespectFilenamePrecedenceAndQuotedFields() {
        for ((name, mime, delimiter) in listOf(Triple("table.tsv", "text/csv", '\t'),
            Triple("table.csv", "text/tab-separated-values", ','), Triple("table.txt", "text/tsv", ','),
            Triple("unknown", "text/tab-separated-values; charset=utf-8", '\t'), Triple("unknown", "text/csv", ','))) {
            val document = TextDocuments.read("\"first\tfield\"\t\"second\r\nline\"\r\n".byteInputStream(), name, mime)
            assertEquals(delimiter, document.defaultCsvDelimiter)
        }
        val cells = CsvPreview.parse("\"first\tfield\"\t\"second\r\nline\"\r\n", '\t')
        assertEquals(listOf(listOf("first\tfield", "second\r\nline")), cells)
    }
    @Test fun configurationDecorationsKeepEveryCharacterAndNeverInterpretValues() {
        val samples = listOf(
            FileKind.ENV to "# comment\r\nexport KEY='text # literal' # comment\r\nOTHER=\"multiline\n${'$'}(echo never) <script>🚀</script>\"\nBROKEN='unterminated\nVALUE=x",
            FileKind.PROPERTIES to "! Comment\r\nescaped\\ key\\:part : <script>🚀</script>\\u0041\\\r\n  # continuation is value\r\nkey=value\\tstill\\qraw\r\n",
            FileKind.DOCKERFILE to "# syntax=docker/dockerfile:1\r\nFROM alpine AS base\r\nRUN echo '<script>🚀</script>' \\\r\n    && echo never\r\nCOPY [\"a\", \"b\"]\r\n",
            FileKind.DOCKERFILE to "# escape=`\nFROM windows\nRUN echo x`\n FROM continuation\n"
        )
        for ((kind, source) in samples) {
            val html = SyntaxHighlighter.highlight(source, kind)
            assertEquals(kind.name, SyntaxHighlighter.escape(source), undecorate(html))
            assertTrue(html.contains("class=\"literal\""))
            assertTrue(html.contains("class=\"comment\""))
            assertFalse(html.contains("<script>"))
        }
        val properties = SyntaxHighlighter.highlight("k=\\u0041\\\n next", FileKind.PROPERTIES)
        assertTrue(properties.contains("<span class=\"literal\">\\u0041</span>"))
        assertFalse(properties.contains(">A<"))
    }
    @Test fun typeScriptAndTsxRemainEscapedApproximateSource() {
        val samples = listOf(
            FileKind.TYPESCRIPT to "interface Box<T> { readonly value: T }\r\nconst x: number = 1; // comment\nconst t = `hello ${'$'}{name}\nworld`;",
            FileKind.TSX to "type Props = {name: string};\nconst f = <T,>(x: T) => x;\nconst view = <script src=\"https://example.invalid/\">{`🚀`}</script>;\n/* comment */",
            FileKind.TSX to "const broken = <div title=\"unterminated\n<script>never"
        )
        for ((kind, source) in samples) {
            val html = SyntaxHighlighter.highlight(source, kind)
            assertEquals(SyntaxHighlighter.escape(source), undecorate(html))
            assertTrue(html.contains("class=\"literal\""))
            assertFalse(html.contains("<script"))
        }
        assertTrue(SyntaxHighlighter.highlight("<Component />", FileKind.TSX).contains("class=\"key\""))
    }
    @Test fun characterAndTokenLimitsFallBackToCompleteEscapedSourceAndCancel() {
        for (kind in listOf(FileKind.ENV, FileKind.PROPERTIES, FileKind.DOCKERFILE, FileKind.TYPESCRIPT, FileKind.TSX)) {
            val source = "<&>".repeat(RenderPolicy.MAX_HIGHLIGHT_CHARS / 3 + 1)
            assertEquals(SyntaxHighlighter.escape(source), SyntaxHighlighter.highlight(source, kind))
            val many = when (kind) { FileKind.ENV, FileKind.PROPERTIES -> "A=\\u0041 # comment\n"; FileKind.DOCKERFILE -> "RUN echo \\\n"; else -> "const x = 1;\n" }.repeat(12_000)
            val html = SyntaxHighlighter.highlight(many, kind)
            assertTrue(Regex("<span").findAll(html).count() <= RenderPolicy.MAX_HIGHLIGHT_TOKENS)
            assertEquals(SyntaxHighlighter.escape(many), undecorate(html))
            Thread.currentThread().interrupt()
            try { SyntaxHighlighter.highlight("KEY=value", kind); fail("Expected cancellation") }
            catch (_: CancellationException) { } finally { Thread.interrupted() }
        }
    }
    @Test fun binaryTsAndLegacyPropertiesUseExistingValidationAndEncodingRecovery() {
        try { TextDocuments.read(byteArrayOf(0, 71, 0, 0).inputStream(), "recording.ts"); fail("Binary must be rejected") }
        catch (error: DocumentException) { assertEquals(ReadFailure.BINARY, error.reason) }
        val bytes = "name=Grüße\r\nescape=\\u0041".toByteArray(Charsets.ISO_8859_1)
        try { TextDocuments.read(bytes.inputStream(), "old.properties"); fail("Needs explicit recovery") }
        catch (error: DocumentException) { assertEquals(ReadFailure.ENCODING, error.reason) }
        val document = TextDocuments.read(bytes.inputStream(), "old.properties", encoding = TextEncoding.LATIN1)
        assertEquals(FileKind.PROPERTIES, document.kind)
        assertEquals("name=Grüße\r\nescape=\\u0041", document.text)
    }
    private fun undecorate(html: String) = html.replace(Regex("</?span[^>]*>"), "")
}
