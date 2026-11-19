package de.utrost.textr

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CancellationException

class StructuredOutlineTest {
    @Test fun jsonKeepsDuplicatesOrderRawValuesAndUtf16CrLfLocations() {
        val source = "{\r\n\"🚀\": 1e+002, \"🚀\": -0.00, \"a\": [\"\\u0041\", true, null]}"
        val result = StructureParser.parse(source, FileKind.JSON)
        assertTrue(result.available)
        val nodes = result.nodes
        assertEquals(listOf("object", "number", "number", "array", "string", "boolean", "null"), nodes.map { it.type })
        assertEquals(listOf("$", "$[\"🚀\"]@1", "$[\"🚀\"]@2", "$[\"a\"]@1", "$[\"a\"]@1[0]", "$[\"a\"]@1[1]", "$[\"a\"]@1[2]"), nodes.map { it.path })
        assertEquals(listOf(source, "1e+002", "-0.00", "[\"\\u0041\", true, null]", "\"\\u0041\"", "true", "null"), nodes.map { source.substring(it.start, it.end) })
        assertEquals(1, nodes.first().line)
        assertEquals(2, nodes[1].line)
        assertEquals(3, nodes[4].parent)
        val escaped = StructureParser.parse("{\"a\\\"b\\n\":0}", FileKind.JSON)
        assertEquals("$[\"a\\\"b\\n\"]@1", escaped.nodes[1].path)
    }
    @Test fun xmlKeepsNamespaceSiblingsRawAttributesAndWhitespaceSensitiveRanges() {
        val source = "<?xml version=\"1.0\"?>\r\n<r xmlns=\"urn:r\" xmlns:p=\"urn:p\"><p:x id=\"a&amp;b\">  🚀\r\n </p:x><x/><p:x p:id='2'/><!-- <fake/> --><![CDATA[<fake/>]]></r>"
        val result = StructureParser.parse(source, FileKind.XML)
        assertTrue(result.available)
        val nodes = result.nodes
        assertEquals(listOf("r", "p:x", "@id", "x", "p:x", "@p:id"), nodes.map { it.label })
        assertEquals("/{\"urn:r\"}r[1]/{\"urn:p\"}x[2]/@{\"urn:p\"}id", nodes.last().path)
        assertEquals("/{\"urn:r\"}r[1]/{\"urn:r\"}x[1]", nodes[3].path)
        assertEquals("a&amp;b", source.substring(nodes[2].start, nodes[2].end))
        assertEquals("<p:x id=\"a&amp;b\">  🚀\r\n </p:x>", source.substring(nodes[1].start, nodes[1].end))
        assertEquals("<x/>", source.substring(nodes[3].start, nodes[3].end))
        assertEquals(2, nodes[1].line)
        assertEquals(3, nodes.last().line)
    }
    @Test fun malformedUnsupportedAndOverBudgetStructuresOfferNoPartialOutline() {
        for ((kind, source) in listOf(
            FileKind.JSON to "{\"a\":1", FileKind.JSON to "{} {}", FileKind.JSON to "[01]",
            FileKind.JSON to "[".repeat(65) + "0" + "]".repeat(65),
            FileKind.JSON to "[" + "0,".repeat(2_000) + "0]",
            FileKind.JSON to "{\"${"x".repeat(5_000)}\":[" + "0,".repeat(100) + "0]}",
            FileKind.XML to "<r><x></r>", FileKind.XML to "<r a='1' a='2'/>",
            FileKind.XML to "<r><p:x/></r>", FileKind.XML to "<r/>tail",
            FileKind.XML to "<r>".repeat(65) + "</r>".repeat(65),
            FileKind.XML to "<r>" + "<x/>".repeat(2_000) + "</r>",
            FileKind.XML to "<!DOCTYPE r SYSTEM 'file:///etc/passwd'><r/>",
            FileKind.XML to "<!DOCTYPE r [<!ENTITY x SYSTEM 'http://127.0.0.1:1/xxe'>]><r>&x;</r>",
            FileKind.XML to "<!DOCTYPE r [<!ENTITY x 'expanded'>]><r>&x;</r>",
            FileKind.XML to "<r>&undefined;</r>",
            FileKind.XML to "<!ENTITY x 'x'><r/>",
            FileKind.XML to "<r>" + "x".repeat(StructureParser.MAX_CHARS) + "</r>"
        )) {
            val result = StructureParser.parse(source, kind)
            assertFalse("Accepted: ${source.take(80)}", result.available)
            assertTrue(result.nodes.isEmpty())
        }
    }
    @Test fun hugeNumericSpellingIsKeptWithoutNumericConversion() {
        val number = "9".repeat(20_000)
        val result = StructureParser.parse("[$number]", FileKind.JSON)
        assertTrue(result.available)
        assertEquals(number.length, result.nodes[1].end - result.nodes[1].start)
    }
    @Test fun cancellationIsNotReportedAsMalformedInput() {
        Thread.currentThread().interrupt()
        try {
            for (kind in listOf(FileKind.JSON, FileKind.XML)) {
                try { StructureParser.parse("{}", kind); fail("Expected cancellation") }
                catch (_: CancellationException) { }
            }
        } finally { Thread.interrupted() }
    }
}
