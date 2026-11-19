package de.utrost.textr

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.concurrent.CancellationException

class EncodedOutputTest {
    @Test fun encodingsPreserveUnicodeBomAndEveryLineEndingWithoutSubstitution() {
        val source = "A\r\nGrüße\r東京\n🚀" + "x".repeat(8191) + "🚀"
        for (encoding in listOf(TextEncoding.UTF8, TextEncoding.UTF16LE, TextEncoding.UTF16BE)) {
            for (bom in listOf(false, true)) {
                val output = ByteArrayOutputStream()
                val count = EncodedOutput.write(source, encoding, bom, output)
                assertEquals(output.size(), count)
                val decoded = TextDocuments.read(output.toByteArray().inputStream(), "copy.txt", encoding = encoding)
                assertEquals(source, decoded.text)
                assertEquals(encoding.charsetName, decoded.encoding)
            }
        }
        val output = ByteArrayOutputStream()
        EncodedOutput.write("Grüße\r\n\\u0041", TextEncoding.LATIN1, false, output)
        assertArrayEquals("Grüße\r\n\\u0041".toByteArray(Charsets.ISO_8859_1), output.toByteArray())
    }
    @Test fun invalidOrUnrepresentableTextFailsInsteadOfReplacingCharacters() {
        for ((text, encoding) in listOf("🚀" to TextEncoding.LATIN1, "\ud800" to TextEncoding.UTF8)) {
            try { EncodedOutput.write(text, encoding, false, ByteArrayOutputStream()); fail("Expected strict encoding failure") }
            catch (_: java.nio.charset.CharacterCodingException) { }
        }
    }
    @Test fun outputSizeAndCancellationAreBounded() {
        val boundary = ByteArrayOutputStream()
        assertEquals(MAX_FILE_BYTES, EncodedOutput.write("a".repeat(MAX_FILE_BYTES), TextEncoding.UTF8, false, boundary))
        val output = ByteArrayOutputStream()
        try { EncodedOutput.write("é".repeat(MAX_FILE_BYTES / 2 + 1), TextEncoding.UTF8, false, output); fail("Expected output limit") }
        catch (_: IllegalArgumentException) { assertTrue(output.size() <= MAX_FILE_BYTES) }
        var checks = 0
        try { EncodedOutput.write("x".repeat(50_000), TextEncoding.UTF8, false, ByteArrayOutputStream()) { if (++checks > 3) throw CancellationException() }; fail("Expected cancellation") }
        catch (_: CancellationException) { assertTrue(checks <= 4) }
    }
}
