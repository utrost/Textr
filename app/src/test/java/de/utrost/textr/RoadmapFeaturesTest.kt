package de.utrost.textr

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class RoadmapFeaturesTest {
    private fun doc(text: String) = TextDocument("private/source.txt", text, FileKind.TEXT, text.toByteArray().size)
    @Test fun indentationPreservesMixedEndingsAndFormsOneUndoStep() {
        val source = "alpha\r\n\tbeta\n gamma\rdelta"
        val buffer = EditBuffer(source)
        buffer.select(EditSelection(0, source.indexOf("delta")))
        assertTrue(EditorLines.indent(buffer, false))
        assertEquals("  alpha\r\n  \tbeta\n   gamma\rdelta", buffer.text)
        assertTrue(buffer.undo()); assertEquals(source, buffer.text); assertFalse(buffer.canUndo)
        assertTrue(buffer.redo()); assertTrue(EditorLines.indent(buffer, true)); assertEquals(source, buffer.text)
    }
    @Test fun indentationHandlesReverseSelectionAndEmptyLastLine() {
        val buffer = EditBuffer("a\r\nb\r\n")
        buffer.select(EditSelection(4, 0)); assertTrue(EditorLines.indent(buffer, false))
        assertEquals("  a\r\n  b\r\n", buffer.text)
        assertTrue(buffer.selection.start > buffer.selection.end)
        assertTrue(EditorLines.goTo(buffer, 3)); assertEquals(buffer.text.length, buffer.selection.start)
        assertTrue(EditorLines.indent(buffer, false)); assertTrue(buffer.text.endsWith("\r\n  "))
        assertFalse(EditorLines.goTo(buffer, 0)); assertFalse(EditorLines.goTo(buffer, 4))
    }
    @Test fun unindentRemovesOnlyOneTabOrTwoAsciiSpaces() {
        val buffer = EditBuffer("\t\talpha\n   beta\n\u00a0gamma")
        buffer.select(EditSelection(0, buffer.text.length)); assertTrue(EditorLines.indent(buffer, true))
        assertEquals("\talpha\n beta\n\u00a0gamma", buffer.text)
        assertTrue(buffer.undo()); assertEquals("\t\talpha\n   beta\n\u00a0gamma", buffer.text)
    }
    @Test fun indentationRefusesOverBudgetWithoutChangingHistoryOrSelection() {
        val buffer = EditBuffer("x".repeat(EditBuffer.MAX_LINE_CHARS))
        buffer.select(EditSelection(7)); assertFalse(EditorLines.indent(buffer, false))
        assertEquals(EditSelection(7), buffer.selection); assertFalse(buffer.canUndo)
    }
    @Test fun gotoTreatsCrLfCrAndLfAsSingleLogicalBoundaries() {
        val buffer = EditBuffer("a\r\nb\rc\nd")
        listOf(0,3,5,7).forEachIndexed { index, offset -> assertTrue(EditorLines.goTo(buffer,index+1)); assertEquals(offset,buffer.selection.start) }
        assertFalse(buffer.dirty); assertFalse(buffer.canUndo)
    }
    @Test fun comparisonFiltersRemainIndependentAndKeepOriginalRanges() {
        val left = doc("a \tb\r\n\u00a0c\r\n")
        val right = doc("ab\n\u00a0c\n")
        assertFalse(LineDiff.compare(left,right).identical)
        assertFalse(LineDiff.compare(left,right,DiffOptions(ignoreWhitespace=true)).identical)
        assertFalse(LineDiff.compare(left,right,DiffOptions(ignoreLineEndings=true)).identical)
        val filtered=LineDiff.compare(left,right,DiffOptions(true,true))
        assertTrue(filtered.identical)
        assertEquals("a \tb\r\n",left.text.substring(filtered.left[0].start,filtered.left[0].end))
        assertFalse(LineDiff.compare(doc("\u00a0x\n"),doc("x\n"),DiffOptions(true,true)).identical)
        assertFalse(LineDiff.compare(doc("a\n\n"),doc("a\n"),DiffOptions(true,true)).identical)
    }
    @Test fun endingFilterDefinesMissingFinalTerminatorWithoutDroppingBlankLines() {
        assertTrue(LineDiff.compare(doc("a\n"),doc("a"),DiffOptions(ignoreLineEndings=true)).identical)
        assertFalse(LineDiff.compare(doc("a\n\n"),doc("a"),DiffOptions(ignoreLineEndings=true)).identical)
    }
    @Test fun unifiedDiffFixturesUseExactTextIncludingCrLfAndMissingNewline() {
        val cases=listOf(
            "" to "new\n", "old\n" to "", "old" to "new", "old\r\n" to "new\r\n",
            "a\r\nb\r\n" to "a\nb\n", "a\rb\r" to "a\rc\r", "α🚀\n" to "β🚀\n",
            "a\n" to "a", "a" to "a\n", "" to "", "same\n" to "same\n",
            (0..30).joinToString("\n",postfix="\n") { "line$it" } to (0..30).joinToString("\n",postfix="\n") { if(it==3 || it==20) "changed$it" else "line$it" },
            "a\nb\nc\nd\ne\nf\ng\nh\ni\nj\n" to "A\nb\nc\nd\ne\nf\ng\nH\ni\nj\n"
        )
        val directory=File("build/reports/unified-diff-fixtures").apply { mkdirs() }
        cases.forEachIndexed { index,(left,right) ->
            val patch=UnifiedDiff.export(doc(left),doc(right)); assertNotNull("case $index",patch)
            assertFalse(patch!!.contains("private/source"))
            val target=File(directory,"case-$index").apply { mkdirs() }
            File(target,"before").writeText(left);File(target,"after").writeText(right);File(target,"changes.patch").writeText(patch)
        }
    }
    @Test fun unifiedDiffRefusesInputLimitsRatherThanReturningPartialPatch() {
        assertNull(UnifiedDiff.export(doc("a".repeat(LineDiff.MAX_CHARS+1)),doc("b")))
    }
    @Test fun jsonLinesIncludesValidDuplicateKeysInvalidAndBlankPhysicalLines() {
        val source="{\"x\":1,\"x\":2}\r\n\r\nbroken\nnull\n{} {}\n[1,2]"
        val outline=JsonLines.parse(source); assertTrue(outline.available)
        assertEquals(listOf("records","record","blank_record","invalid_record","record","invalid_record","record"),outline.nodes.map{it.type})
        assertEquals((1..6).toList(),outline.nodes.drop(1).map{it.line})
        assertEquals(source,outline.nodes.drop(1).joinToString(""){source.substring(it.start,it.end)})
    }
    @Test fun jsonLinesMarksOversizedAndDeepRecordsAndFallsBackForGlobalLimits() {
        assertEquals("invalid_record",JsonLines.parse("\""+"x".repeat(JsonLines.MAX_RECORD_CHARS)+"\"").nodes[1].type)
        assertEquals("invalid_record",JsonLines.parse("[".repeat(65)+"0"+"]".repeat(65)).nodes[1].type)
        assertFalse(JsonLines.parse("{}\n".repeat(JsonLines.MAX_RECORDS+1)).available)
        assertFalse(JsonLines.parse("x".repeat(StructureParser.MAX_CHARS+1)).available)
    }
    @Test fun focusedFormatsUseKnownExtensionsBeforeMimeAndHaveOverrides() {
        assertEquals(FileKind.JSON_LINES,TextDocuments.kind("events.ndjson","text/plain"))
        assertEquals(FileKind.JSON_LINES,TextDocuments.kind("unknown","application/x-ndjson; charset=utf-8"))
        assertEquals(FileKind.TEXT,TextDocuments.kind("known.txt","application/x-ndjson"))
        assertEquals(FileKind.ASCIIDOC,TextDocuments.kind("notes.adoc",null))
        assertEquals(FileKind.RESTRUCTUREDTEXT,TextDocuments.kind("notes.rst",null))
        assertEquals(FileKind.ASCIIDOC,TextDocuments.kind("unknown","text/asciidoc"))
    }
    @Test fun markupHighlightingNeverInterpretsIncludesAndPreservesAllSource() {
        for ((kind,text) in listOf(FileKind.ASCIIDOC to "= Title\r\ninclude::/private/file[]\n----\n<script>\n",FileKind.RESTRUCTUREDTEXT to "Title\n=====\n.. include:: /private/file\n\n:field: value\n")) {
            val highlighted=SyntaxHighlighter.highlight(text,kind)
            assertTrue(highlighted.contains("<span"))
            assertEquals(text,org.jsoup.parser.Parser.unescapeEntities(highlighted.replace(Regex("</?span[^>]*>"),""),false))
            assertFalse(highlighted.contains("<script>"))
            assertNull(DocumentRenderer.prepare(TextDocument("notes",text,kind,text.length)).preview)
        }
    }
    @Test fun markdownTasksAreReadOnlyAndDoNotRewriteCodeOrMalformedMarkers() {
        val text="- [ ] open\n- [x] done\n  - [X] nested\n- [z] ordinary\n\n```md\n- [x] code\n```"
        val html=DocumentRenderer.markdown(text)
        assertTrue(html.contains("☐ Unchecked task: open"));assertTrue(html.contains("☑ Checked task: done"));assertTrue(html.contains("☑ Checked task: nested"))
        assertTrue(html.contains("[z] ordinary"));assertTrue(html.contains("[x] code"))
        assertFalse(html.contains("<input"));assertFalse(html.contains("onclick"))
        assertTrue(DocumentRenderer.markdown("- [x] yes",translate={if(it=="Checked task") "Erledigt" else it}).contains("☑ Erledigt: yes"))
    }
}
