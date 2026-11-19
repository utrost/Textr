package de.utrost.textr

/** Physical line ranges include their original terminators, with no synthetic EOF line. */
data class SourceLine(val start: Int, val contentEnd: Int, val end: Int, val hash: Int)
enum class DiffKind { SAME, DELETE, ADD }
data class DiffRow(val kind: DiffKind, val left: Int? = null, val right: Int? = null)
enum class DiffLimit { INPUT, LINES, LINE_LENGTH, DISTANCE, WORK }
data class LineDiffResult(val left: List<SourceLine> = emptyList(), val right: List<SourceLine> = emptyList(),
    val rows: List<DiffRow> = emptyList(), val limit: DiffLimit? = null) {
    val identical get() = limit == null && rows.all { it.kind == DiffKind.SAME }
}
data class DiffDisplayRow(val left: Int?, val right: Int?, val change: Int?)

/** Bounded Myers diff; exact UTF-16 comparison, including terminators. No normalization. */
object LineDiff {
    const val MAX_BYTES = 512 * 1024
    const val MAX_CHARS = 256 * 1024
    const val MAX_LINES = 10_000
    const val MAX_LINE_CHARS = 16_384
    const val MAX_DISTANCE = 512
    const val MAX_WORK = 2_000_000
    private class Limit(val reason: DiffLimit) : RuntimeException()
    private fun requireBudget(value: Boolean, limit: DiffLimit) { if (!value) throw Limit(limit) }
    private fun split(text: String): List<SourceLine> = buildList {
        var start = 0
        while (start < text.length) {
            RenderPolicy.checkCancellation()
            requireBudget(size < MAX_LINES, DiffLimit.LINES)
            var end = start
            var hash = 0
            while (end < text.length && text[end] !in "\r\n") {
                if (end % 1024 == 0) RenderPolicy.checkCancellation()
                requireBudget(end - start < MAX_LINE_CHARS, DiffLimit.LINE_LENGTH)
                hash = 31 * hash + text[end++].code
            }
            val contentEnd = end
            if (text.getOrNull(end) == '\r') hash = 31 * hash + text[end++].code
            if (text.getOrNull(end) == '\n') hash = 31 * hash + text[end++].code
            add(SourceLine(start, contentEnd, end, hash)); start = end
        }
    }
    fun compare(left: TextDocument, right: TextDocument): LineDiffResult {
        RenderPolicy.checkCancellation()
        return try {
            requireBudget(left.bytes in 0..MAX_BYTES && right.bytes in 0..MAX_BYTES && left.text.length <= MAX_CHARS && right.text.length <= MAX_CHARS, DiffLimit.INPUT)
            val a = split(left.text); val b = split(right.text)
            var work = 0
            fun step() {
                if (++work % 1024 == 0) RenderPolicy.checkCancellation()
                requireBudget(work <= MAX_WORK, DiffLimit.WORK)
            }
            fun equal(x: Int, y: Int): Boolean {
                step()
                val first = a[x]; val second = b[y]
                if (first.hash != second.hash || first.end - first.start != second.end - second.start) return false
                for (index in 0 until first.end - first.start) {
                    step()
                    if (left.text[first.start + index] != right.text[second.start + index]) return false
                }
                return true
            }
            val offset = MAX_DISTANCE + 1
            val frontier = IntArray(2 * MAX_DISTANCE + 3) { -1 }
            frontier[offset + 1] = 0
            val trace = mutableListOf<IntArray>()
            for (distance in 0..MAX_DISTANCE) {
                RenderPolicy.checkCancellation()
                trace.add(frontier.copyOf())
                for (diagonal in -distance..distance step 2) {
                    step()
                    val at = offset + diagonal
                    var x = if (diagonal == -distance || (diagonal != distance && frontier[at - 1] < frontier[at + 1])) frontier[at + 1] else frontier[at - 1] + 1
                    var y = x - diagonal
                    while (x < a.size && y < b.size && equal(x, y)) { x++; y++ }
                    frontier[at] = x
                    if (x >= a.size && y >= b.size) {
                        val rows = mutableListOf<DiffRow>()
                        var backX = a.size; var backY = b.size
                        for (depth in trace.lastIndex downTo 0) {
                            RenderPolicy.checkCancellation()
                            val previous = trace[depth]
                            val k = backX - backY
                            val previousK = if (k == -depth || (k != depth && previous[offset + k - 1] < previous[offset + k + 1])) k + 1 else k - 1
                            val previousX = previous[offset + previousK]
                            val previousY = previousX - previousK
                            while (backX > previousX && backY > previousY) {
                                if (rows.size % 256 == 0) RenderPolicy.checkCancellation()
                                rows.add(DiffRow(DiffKind.SAME, --backX, --backY))
                            }
                            if (depth > 0) {
                                if (backX == previousX) rows.add(DiffRow(DiffKind.ADD, right = --backY))
                                else rows.add(DiffRow(DiffKind.DELETE, left = --backX))
                            }
                        }
                        rows.reverse()
                        RenderPolicy.checkCancellation()
                        return LineDiffResult(a, b, rows)
                    }
                }
            }
            throw Limit(DiffLimit.DISTANCE)
        } catch (limit: Limit) { LineDiffResult(limit = limit.reason) }
    }

    /** Wide rows pair replacements; one shared list provides linked vertical scrolling. */
    fun display(result: LineDiffResult, wide: Boolean): List<DiffDisplayRow> = buildList {
        var index = 0; var change = 0
        while (index < result.rows.size) {
            RenderPolicy.checkCancellation()
            val row = result.rows[index]
            if (row.kind == DiffKind.SAME) { add(DiffDisplayRow(row.left, row.right, null)); index++; continue }
            val first = index
            while (index < result.rows.size && result.rows[index].kind != DiffKind.SAME) index++
            val block = result.rows.subList(first, index)
            if (!wide) block.forEach { add(DiffDisplayRow(it.left, it.right, change)) }
            else {
                val left = block.mapNotNull { it.left }; val right = block.mapNotNull { it.right }
                for (at in 0 until maxOf(left.size, right.size)) add(DiffDisplayRow(left.getOrNull(at), right.getOrNull(at), change))
            }
            change++
        }
    }
}
