package de.utrost.textr

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

class TextDocumentsTest {
    @Test fun `reads UTF-8 without altering line endings or whitespace`() {
        val text = "Hello, Grüße 👋\r\n  second line\t\n"
        assertEquals(text, TextDocuments.read(text.byteInputStream(), "notes.txt").text)
    }

    @Test fun `accepts empty and exactly five MiB but refuses one extra byte`() {
        assertEquals("", TextDocuments.read(ByteArrayInputStream(byteArrayOf()), "empty.txt").text)
        val boundary = ByteArray(MAX_FILE_BYTES) { 'a'.code.toByte() }
        assertEquals(MAX_FILE_BYTES, TextDocuments.read(ByteArrayInputStream(boundary), "max.txt").bytes)
        assertFailure(ReadFailure.TOO_LARGE, boundary + byteArrayOf(97))
    }

    @Test fun `stops an unknown-length stream at limit plus one`() {
        var bytesRead = 0
        val endless = object : InputStream() {
            override fun read(): Int { bytesRead++; return 97 }
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                b.fill(97, off, off + len); bytesRead += len; return len
            }
        }
        try { TextDocuments.read(endless, "endless.log"); fail("Expected size rejection") }
        catch (error: DocumentException) { assertEquals(ReadFailure.TOO_LARGE, error.reason) }
        assertEquals(MAX_FILE_BYTES + 1, bytesRead)
    }

    @Test fun `recognizes BOMs and validates UTF-16`() {
        val source = "Grüße\n東京"
        assertEquals(source, TextDocuments.read(ByteArrayInputStream(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + source.toByteArray()), "bom.txt").text)
        for ((charset, bom) in listOf(Charsets.UTF_16LE to byteArrayOf(-1, -2), Charsets.UTF_16BE to byteArrayOf(-2, -1))) {
            assertEquals(source, TextDocuments.read(ByteArrayInputStream(bom + source.toByteArray(charset)), "utf16.txt").text)
        }
        assertFailure(ReadFailure.ENCODING, byteArrayOf(-1, -2, 65))
    }

    @Test fun `rejects binary and malformed UTF-8`() {
        assertFailure(ReadFailure.BINARY, byteArrayOf(65, 0, 66))
        assertFailure(ReadFailure.BINARY, byteArrayOf(65, 1, 66))
        assertFailure(ReadFailure.ENCODING, byteArrayOf(0xC3.toByte(), 0x28))
    }

    @Test fun `detects formats without treating unknown config as a specific syntax`() {
        assertEquals(FileKind.MARKDOWN, TextDocuments.kind("README.MD", "application/octet-stream"))
        assertEquals(FileKind.YAML, TextDocuments.kind("settings", "application/yaml"))
        assertEquals(FileKind.TEXT, TextDocuments.kind("server.conf", "text/plain"))
        assertEquals(FileKind.INI, TextDocuments.kind("server.conf", "application/x-ini"))
        assertEquals(FileKind.CSV, TextDocuments.kind("spreadsheet.csv", "text/csv"))
        assertEquals(FileKind.JSON, TextDocuments.kind("data", "application/json; charset=utf-8"))
    }

    private fun assertFailure(reason: ReadFailure, bytes: ByteArray) {
        try { TextDocuments.read(ByteArrayInputStream(bytes), "file.txt"); fail("Expected $reason") }
        catch (error: DocumentException) { assertEquals(reason, error.reason) }
    }
}
