package de.utrost.textr

import org.junit.Assert.*
import org.junit.Test

class EditBufferTest {
    @Test fun sourceBomMetadataSupportsFidelityWithoutManufacturingOne() {
        for ((encoding, bom) in listOf(TextEncoding.UTF8 to byteArrayOf(0xef.toByte(), 0xbb.toByte(), 0xbf.toByte()), TextEncoding.UTF16LE to byteArrayOf(0xff.toByte(), 0xfe.toByte()), TextEncoding.UTF16BE to byteArrayOf(0xfe.toByte(), 0xff.toByte()))) {
            val bytes = "Grüße\r\n".toByteArray(java.nio.charset.Charset.forName(encoding.charsetName))
            assertTrue(TextDocuments.read((bom + bytes).inputStream(), "a.txt").hasBom)
            assertFalse(TextDocuments.read(bytes.inputStream(), "a.txt", encoding = encoding).hasBom)
        }
        assertNotEquals(textFingerprint("\uD800"), textFingerprint("\uD801"))
    }
    @Test fun editsUndoRedoSelectionsAndDirtyBaseline() {
        val buffer = EditBuffer("a\r\nb\n")
        buffer.select(EditSelection(3, 4))
        assertTrue(buffer.replace(3, 4, "word"))
        assertEquals("a\r\nword\n", buffer.text); assertTrue(buffer.dirty)
        assertTrue(buffer.undo()); assertEquals("a\r\nb\n", buffer.text); assertEquals(EditSelection(3, 4), buffer.selection); assertFalse(buffer.dirty)
        assertTrue(buffer.redo()); assertEquals(EditSelection(7), buffer.selection)
        buffer.markSaved(); assertFalse(buffer.dirty)
        buffer.undo(); assertTrue(buffer.dirty)
        buffer.replace(0, 1, "Z"); assertFalse(buffer.canRedo)
    }
    @Test fun boundedHistoryAndAtomicOverflow() {
        val buffer = EditBuffer("")
        repeat(30) { buffer.replace(buffer.text.length, buffer.text.length, "x") }
        var count = 0; while (buffer.undo()) count++
        assertEquals(20, count); assertEquals("x".repeat(10), buffer.text)
        val full = EditBuffer(("a".repeat(63) + "\n").repeat(EditBuffer.MAX_CHARS / 64))
        val revision = full.revision
        assertFalse(full.replace(0, 0, "b")); assertEquals(revision, full.revision); assertFalse(full.dirty)
        repeat(8) { full.replace(0, full.text.length, if (it % 2 == 0) ("b".repeat(63) + "\n").repeat(EditBuffer.MAX_CHARS / 64) else ("a".repeat(63) + "\n").repeat(EditBuffer.MAX_CHARS / 64)) }
        assertTrue(full.historyCharacters <= EditBuffer.MAX_HISTORY_CHARS)
    }
    @Test fun literalFindAndOneStepReplaceAll() {
        val buffer = EditBuffer("cat scatter CAT cat_ [cat] .*")
        val options = LiteralOptions(wholeWord = true)
        assertEquals(3, buffer.matches("cat", options).ranges.size)
        assertEquals(2, buffer.matches("cat", options.copy(caseSensitive = true)).ranges.size)
        assertEquals(1, buffer.matches(".*", options).ranges.size)
        assertEquals(3, buffer.replaceAll("cat", "dog", options))
        assertEquals("dog scatter dog cat_ [dog] .*", buffer.text)
        buffer.undo(); assertEquals("cat scatter CAT cat_ [cat] .*", buffer.text)
        val many = EditBuffer("x".repeat(201)); assertTrue(many.matches("x", LiteralOptions()).truncated)
        val full = EditBuffer(("a".repeat(63) + "\n").repeat(EditBuffer.MAX_CHARS / 64))
        assertNull(full.replaceAll("a", "bb", LiteralOptions())); assertFalse(full.dirty)
        assertEquals(0, full.replaceAll("", "x", LiteralOptions()))
        assertFalse(EditBuffer.accepts("x".repeat(EditBuffer.MAX_LINE_CHARS + 1)))
        assertFalse(EditBuffer.accepts("\n".repeat(EditBuffer.MAX_LINES)))
        val one = EditBuffer("x".repeat(EditBuffer.MAX_LINE_CHARS))
        assertFalse(one.replace(0, 0, "y")); assertNull(one.replaceAll("x", "xx", LiteralOptions()))
    }
    @Test fun linearUnicodeMatchingAgreesWithReaderBoundariesAndHandlesOverlap() {
        val text = "𐐀 𐐨 e\u0301 e 🚀 aaa"
        val buffer = EditBuffer(text)
        val options = LiteralOptions(wholeWord = true)
        assertEquals(SourceSearch.find(text, SourceSearchOptions("𐐨", wholeWord = true)).matches.map { it.start until it.end }, buffer.matches("𐐨", options).ranges)
        assertEquals(2, buffer.countMatches("𐐨", options))
        assertEquals(1, buffer.countMatches("e", options))
        val overlap = EditBuffer("aaa"); assertEquals(1, overlap.replaceAll("aa", "b", LiteralOptions())); assertEquals("ba", overlap.text)
        val repeated = EditBuffer(("a".repeat(63) + "\n").repeat(EditBuffer.MAX_CHARS / 64))
        assertEquals(0, repeated.countMatches("a".repeat(999) + "b", LiteralOptions()))
        assertEquals(EditBuffer.MAX_CHARS / 64 * 63, repeated.countMatches("a", LiteralOptions()))
    }
    @Test fun insertedNewlinesPreserveUniformAndMixedPolicies() {
        assertEquals("a\r\nb\r\nc\r", editorInsertion("a\nb\r\nc\r", "CRLF"))
        assertEquals("a\rb\r\nc\r", editorInsertion("a\nb\r\nc\r", "CR"))
        assertEquals("a\nb\r\n", editorInsertion("a\nb\r\n", "LF + CRLF"))
    }
}
