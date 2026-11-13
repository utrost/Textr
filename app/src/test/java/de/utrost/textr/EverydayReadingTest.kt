package de.utrost.textr

import org.junit.Assert.*
import org.junit.Test
import java.nio.charset.Charset
import java.util.concurrent.CancellationException

class EverydayReadingTest {
    private fun read(bytes: ByteArray, encoding: TextEncoding? = null) = TextDocuments.read(bytes.inputStream(), "note.txt", encoding = encoding)
    private fun failure(reason: ReadFailure, block: () -> Unit) {
        try { block(); fail("Expected $reason") } catch (error: DocumentException) { assertEquals(reason, error.reason) }
    }

    @Test fun explicitLegacyEncodingsRecoverTextWithoutLossyAutomaticFallback() {
        val text = "Café costs €5 — résumé"
        val bytes = text.toByteArray(Charset.forName("windows-1252"))
        failure(ReadFailure.ENCODING) { read(bytes) }
        val doc = read(bytes, TextEncoding.WINDOWS1252)
        assertEquals(text, doc.text); assertEquals("windows-1252", doc.encoding); assertEquals(bytes.size, doc.bytes)
        assertEquals("Café", read("Café".toByteArray(Charsets.ISO_8859_1), TextEncoding.LATIN1).text)
        failure(ReadFailure.ENCODING) { read(byteArrayOf(0x81.toByte()), TextEncoding.WINDOWS1252) }
    }

    @Test fun utf16WithoutBomNeedsExplicitEndianAndMatchingBomsAreRemoved() {
        for ((charset, encoding, bom) in listOf(
            Triple(Charsets.UTF_16LE, TextEncoding.UTF16LE, byteArrayOf(0xff.toByte(), 0xfe.toByte())),
            Triple(Charsets.UTF_16BE, TextEncoding.UTF16BE, byteArrayOf(0xfe.toByte(), 0xff.toByte())))) {
            val text = "Hello 🌍\r\nGrüße"
            val bytes = text.toByteArray(charset)
            failure(ReadFailure.BINARY) { read(bytes) }
            assertEquals(text, read(bytes, encoding).text)
            assertEquals(text, read(bom + bytes).text)
            assertEquals(text, read(bom + bytes, encoding).text)
            failure(ReadFailure.ENCODING) { read(bom + bytes, TextEncoding.UTF8) }
            failure(ReadFailure.ENCODING) { read(bytes + byteArrayOf(0), encoding) }
        }
        assertEquals("hello", read(byteArrayOf(0xef.toByte(), 0xbb.toByte(), 0xbf.toByte()) + "hello".toByteArray(), TextEncoding.UTF8).text)
    }

    @Test fun manualDecodingRetainsBinaryAndByteLimits() {
        for (encoding in TextEncoding.entries) {
            failure(ReadFailure.BINARY) { read(byteArrayOf(0, 0), encoding) }
            failure(ReadFailure.TOO_LARGE) { read(ByteArray(MAX_FILE_BYTES + 1) { 65 }, encoding) }
        }
        assertEquals(MAX_FILE_BYTES, read(ByteArray(MAX_FILE_BYTES) { 65 }, TextEncoding.LATIN1).text.length)
    }

    @Test fun documentMetadataCountsLogicalLinesAndMixedEndings() {
        assertEquals(TextInfo(1, ""), TextInfo.inspect(""))
        assertEquals(TextInfo(1, ""), TextInfo.inspect("α🌍"))
        assertEquals(TextInfo(4, "LF + CRLF + CR"), TextInfo.inspect("α\r\nβ\rγ\n"))
        assertEquals(TextInfo(2, "CRLF"), TextInfo.inspect("\r\n"))
        assertEquals(TextInfo(2, "LF"), TextInfo.inspect("\n"))
    }

    @Test fun searchSupportsLiteralCaseWholeWordsAndLineNumbers() {
        val text = "Cat cat concatenate\r\ncat_cat cat\rCAT\ncat."
        assertEquals(8, SourceSearch.find(text, SourceSearchOptions("cat")).matches.size)
        val result = SourceSearch.find(text, SourceSearchOptions("cat", matchCase = true, wholeWord = true))
        assertEquals(listOf(1, 2, 4), result.matches.map { it.line })
        assertTrue(result.matches.all { text.substring(it.start, it.end) == "cat" })
        assertEquals(1, SourceSearch.find("[a.*] .*", SourceSearchOptions("[a.*]")).matches.size)
        assertTrue(SourceSearch.find(text, SourceSearchOptions("missing")).matches.isEmpty())
    }

    @Test fun unicodeBoundariesIncludeCombiningMarksAndSupplementaryLetters() {
        assertEquals(1, SourceSearch.find("e\u0301 e", SourceSearchOptions("e", wholeWord = true)).matches.size)
        val deseretUpper = String(Character.toChars(0x10400))
        val deseretLower = String(Character.toChars(0x10428))
        val text = "$deseretUpper $deseretLower x${deseretLower}x"
        val matches = SourceSearch.find(text, SourceSearchOptions(deseretLower, wholeWord = true)).matches
        assertEquals(listOf(0, 3), matches.map { it.start })
        assertEquals(listOf(2, 5), matches.map { it.end })
        assertEquals(1, SourceSearch.find(text, SourceSearchOptions(deseretLower, true, true)).matches.size)
    }

    @Test(timeout = 5000) fun searchBudgetsAndRepetitiveWorstCaseRemainBounded() {
        val options = SourceSearchOptions("a".repeat(999) + "b")
        assertTrue(SourceSearch.find("a".repeat(MAX_FILE_BYTES), options).matches.isEmpty())
        assertTrue(SourceSearch.find("a ".repeat(201), SourceSearchOptions("a")).truncated)
        assertFalse(SourceSearch.find("a ".repeat(200), SourceSearchOptions("a")).truncated)
        assertEquals(200, SourceSearch.find("a ".repeat(10000), SourceSearchOptions("a")).matches.size)
        assertTrue(SourceSearch.find("text", SourceSearchOptions()).matches.isEmpty())
        try { SourceSearch.find("text", SourceSearchOptions("a".repeat(1001))); fail() } catch (_: IllegalArgumentException) { }
    }

    @Test fun nonoverlappingMatchesAndCrLfChunkBoundaryStayAccurate() {
        assertEquals(listOf(0, 2), SourceSearch.find("aaaaa", SourceSearchOptions("aa")).matches.map { it.start })
        val text = "x".repeat(1023) + "\r\n<needle&🌍>"
        val match = SourceSearch.find(text, SourceSearchOptions("<needle&🌍>")).matches.single()
        assertEquals(2, match.line)
        val page = DocumentRenderer.page(DocumentRenderer.prepare(TextDocument("x.txt", text, FileKind.TEXT, text.length)),
            DisplayMode.PLAIN, false, true, true, match = match).openStream().bufferedReader().readText()
        assertTrue(page.contains("<mark id=\"source-match\">&lt;needle&amp;🌍&gt;</mark>"))
        assertFalse(page.contains("<needle"))
        assertTrue(page.contains("data-line=\"2\""))
    }

    @Test fun selectedMatchAcrossStreamChunksKeepsExactCharacters() {
        val text = "z".repeat(1020) + "hello🌍world" + "z".repeat(1024)
        val match = SourceSearch.find(text, SourceSearchOptions("hello🌍world")).matches.single()
        val html = DocumentRenderer.page(PreparedDocument(TextDocument("x", text, FileKind.TEXT, text.length), null),
            DisplayMode.PLAIN, true, true, match = match).openStream().bufferedReader().readText()
        val body = html.substringAfter("<pre>").substringBefore("</pre>")
        assertEquals(text, body.replace(Regex("<[^>]*>"), ""))
        assertEquals(1, Regex("<mark ").findAll(body).count())
        assertEquals(1, Regex("</mark>").findAll(body).count())
    }

    @Test fun cancelledSearchStopsBeforeReturningResults() {
        Thread.currentThread().interrupt()
        try {
            try { SourceSearch.find("text", SourceSearchOptions("text")); fail() } catch (_: CancellationException) { }
        } finally { Thread.interrupted() }
    }
}
