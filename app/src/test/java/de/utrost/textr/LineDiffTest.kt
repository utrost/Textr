package de.utrost.textr

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CancellationException

class LineDiffTest {
    private fun document(text: String) = TextDocument("fixture.txt", text, FileKind.TEXT, text.toByteArray().size)
    @Test fun emptyIdenticalUnicodeWhitespaceAndEveryTerminatorAreExact() {
        for (source in listOf("", "a", "a\n", "a\r\n", "a\rb\n", "🚀\nGrüße\r\n東京", " \t\n")) {
            val result = LineDiff.compare(document(source), document(source))
            assertNull(result.limit); assertTrue(result.identical); verify(source, source, result)
        }
        for ((left, right) in listOf("" to "\n", "a" to "a\n", "a\r\n" to "a\n", "é" to "e\u0301", "a" to "A", "a " to "a")) {
            val result = LineDiff.compare(document(left), document(right))
            assertNull(result.limit); assertFalse(result.identical); verify(left, right, result)
        }
        assertEquals(1, LineDiff.compare(document("a\n"), document("a\n")).left.size)
        assertEquals(0, LineDiff.compare(document(""), document("")).left.size)
    }
    @Test fun repeatedLinesAndDisjointInputsProduceDeterministicShortestScripts() {
        val random = java.util.Random(90)
        repeat(400) {
            val a = List(random.nextInt(9)) { listOf("a\n", "b\n", "c\r\n", "🚀\n")[random.nextInt(4)] }
            val b = List(random.nextInt(9)) { listOf("a\n", "b\n", "c\r\n", "🚀\n")[random.nextInt(4)] }
            val source = a.joinToString(""); val target = b.joinToString("")
            val result = LineDiff.compare(document(source), document(target))
            assertNull(result.limit); verify(source, target, result)
            val lcs = Array(a.size + 1) { IntArray(b.size + 1) }
            for (i in a.indices.reversed()) for (j in b.indices.reversed()) lcs[i][j] = if (a[i] == b[j]) lcs[i + 1][j + 1] + 1 else maxOf(lcs[i + 1][j], lcs[i][j + 1])
            assertEquals(a.size + b.size - 2 * lcs[0][0], result.rows.count { it.kind != DiffKind.SAME })
            assertEquals(result, LineDiff.compare(document(source), document(target)))
        }
        val result = LineDiff.compare(document("a\nb\nc\n"), document("x\ny\nz\n"))
        assertEquals(listOf(DiffKind.DELETE, DiffKind.DELETE, DiffKind.DELETE, DiffKind.ADD, DiffKind.ADD, DiffKind.ADD), result.rows.map { it.kind })
    }
    @Test fun oversizedAndAdversarialInputsNeverReportIdenticalOrPartialResults() {
        for ((left, right, reason) in listOf(
            Triple("a".repeat(LineDiff.MAX_CHARS + 1), "", DiffLimit.INPUT),
            Triple("x\n".repeat(LineDiff.MAX_LINES + 1), "", DiffLimit.LINES),
            Triple("x".repeat(LineDiff.MAX_LINE_CHARS + 1), "", DiffLimit.LINE_LENGTH),
            Triple("a\n".repeat(300), "b\n".repeat(300), DiffLimit.DISTANCE))) {
            val result = LineDiff.compare(document(left), document(right))
            assertEquals(reason, result.limit); assertFalse(result.identical); assertTrue(result.rows.isEmpty())
        }
        // "Aa" and "BB" collide under the line hash; exact comparison still has a work cap.
        val prefix = "x".repeat(600)
        val adversarial = LineDiff.compare(document((prefix + "Aa\n").repeat(300)), document((prefix + "BB\n").repeat(300)))
        assertEquals(DiffLimit.WORK, adversarial.limit); assertFalse(adversarial.identical)
        val tooManyBytes = document("x").copy(bytes = LineDiff.MAX_BYTES + 1)
        assertEquals(DiffLimit.INPUT, LineDiff.compare(tooManyBytes, tooManyBytes).limit)
        val long = "x".repeat(LineDiff.MAX_LINE_CHARS)
        val result = LineDiff.compare(document(long), document(long))
        assertTrue(result.identical)
    }
    @Test fun unifiedAndWideViewsKeepBothSourcesAndStableChangeGroups() {
        val a = "first\nold\nold2\ncommon\nend\n"; val b = "first\nnew\ncommon\nlast\n"
        val result = LineDiff.compare(document(a), document(b))
        for (wide in listOf(false, true)) {
            val rows = LineDiff.display(result, wide)
            assertEquals(a, rows.mapNotNull { it.left }.joinToString("") { result.left[it].let { line -> a.substring(line.start, line.end) } })
            assertEquals(b, rows.mapNotNull { it.right }.joinToString("") { result.right[it].let { line -> b.substring(line.start, line.end) } })
            assertEquals(listOf(0, 1), rows.mapNotNull { it.change }.distinct())
        }
    }
    @Test fun cancellationPropagatesInsteadOfReturningAnEqualityResult() {
        Thread.currentThread().interrupt()
        try { LineDiff.compare(document("a"), document("a")); fail("Expected cancellation") }
        catch (_: CancellationException) { } finally { Thread.interrupted() }
    }
    private fun verify(a: String, b: String, result: LineDiffResult) {
        assertEquals(a, result.rows.mapNotNull { it.left }.joinToString("") { result.left[it].let { line -> a.substring(line.start, line.end) } })
        assertEquals(b, result.rows.mapNotNull { it.right }.joinToString("") { result.right[it].let { line -> b.substring(line.start, line.end) } })
        result.rows.filter { it.kind == DiffKind.SAME }.forEach { row ->
            assertEquals(result.left[row.left!!].let { a.substring(it.start, it.end) }, result.right[row.right!!].let { b.substring(it.start, it.end) })
        }
    }
}
