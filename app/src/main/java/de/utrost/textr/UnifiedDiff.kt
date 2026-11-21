package de.utrost.textr

/** UTF-8 decoded-text patch, fixed private headers, three context lines, exact snapshots. */
object UnifiedDiff {
    const val MAX_CHARS = 512 * 1024
    const val MAX_BYTES = 1024 * 1024
    fun export(left: TextDocument, right: TextDocument): String? {
        // Patch tools define lines by LF, not by lone CR. Keep CR bytes in line bodies.
        val result = LineDiff.compare(left, right, lfOnly = true)
        if (result.limit != null) return null
        if (result.identical) return ""
        val rows = result.rows
        val ranges = mutableListOf<IntRange>()
        var at = 0
        while (at < rows.size) {
            RenderPolicy.checkCancellation()
            if (rows[at].kind == DiffKind.SAME) { at++; continue }
            val first = (at - 3).coerceAtLeast(0)
            var last = at
            while (++at < rows.size) {
                if (rows[at].kind != DiffKind.SAME) {
                    if (at - last > 7) break
                    last = at
                }
            }
            val end = (last + 3).coerceAtMost(rows.lastIndex)
            ranges.add(first..end)
            at = end + 1
        }
        val leftBefore = IntArray(rows.size + 1)
        val rightBefore = IntArray(rows.size + 1)
        rows.forEachIndexed { index, row ->
            leftBefore[index + 1] = leftBefore[index] + if (row.left != null) 1 else 0
            rightBefore[index + 1] = rightBefore[index] + if (row.right != null) 1 else 0
        }
        val output = StringBuilder("--- a/original\n+++ b/modified\n")
        fun append(value: String) {
            require(output.length + value.length <= MAX_CHARS)
            output.append(value)
        }
        return try {
            for (range in ranges) {
                val leftCount = leftBefore[range.last + 1] - leftBefore[range.first]
                val rightCount = rightBefore[range.last + 1] - rightBefore[range.first]
                val leftStart = leftBefore[range.first] + if (leftCount > 0) 1 else 0
                val rightStart = rightBefore[range.first] + if (rightCount > 0) 1 else 0
                append("@@ -$leftStart,$leftCount +$rightStart,$rightCount @@\n")
                for (index in range) {
                    RenderPolicy.checkCancellation()
                    val row = rows[index]
                    val isRight = row.kind == DiffKind.ADD
                    val source = if (isRight) right else left
                    val line = if (isRight) result.right[row.right!!] else result.left[row.left!!]
                    append(when (row.kind) { DiffKind.SAME -> " "; DiffKind.ADD -> "+"; DiffKind.DELETE -> "-" })
                    append(source.text.substring(line.start, line.end))
                    if (source.text.getOrNull(line.end - 1) != '\n') append("\n\\ No newline at end of file\n")
                }
            }
            output.toString().takeIf { it.toByteArray(Charsets.UTF_8).size <= MAX_BYTES }
        } catch (_: IllegalArgumentException) { null }
    }
}
