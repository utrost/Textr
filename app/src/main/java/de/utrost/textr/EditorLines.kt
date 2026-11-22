package de.utrost.textr

/** Logical lines preserve CR, LF and CRLF. Indentation touches selected line prefixes only. */
object EditorLines {
    fun starts(text: String): List<Int> = buildList {
        add(0)
        var at = 0
        while (at < text.length) {
            when (text[at++]) {
                '\r' -> { if (text.getOrNull(at) == '\n') at++; add(at) }
                '\n' -> add(at)
            }
        }
    }
    fun goTo(buffer: EditBuffer, line: Int): Boolean {
        val offset = starts(buffer.text).getOrNull(line - 1) ?: return false
        buffer.select(EditSelection(offset)); return true
    }
    /** Two ASCII spaces; unindent removes one tab or at most two spaces. */
    fun indent(buffer: EditBuffer, remove: Boolean): Boolean {
        val text = buffer.text
        val selection = buffer.selection
        val from = minOf(selection.start, selection.end)
        val until = maxOf(selection.start, selection.end)
        val starts = starts(text)
        val first = starts.indexOfLast { it <= from }.coerceAtLeast(0)
        val last = starts.indexOfLast { it <= if (until > from) until - 1 else until }.coerceAtLeast(first)
        data class Prefix(val offset: Int, val removed: Int, val inserted: String)
        val edits = (first..last).map { line ->
            val start = starts[line]
            val count = if (!remove) 0 else if (text.getOrNull(start) == '\t') 1 else
                (0 until 2).takeWhile { text.getOrNull(start + it) == ' ' }.size
            Prefix(start, count, if (remove) "" else "  ")
        }
        if (edits.all { it.removed == 0 && it.inserted.isEmpty() }) return true
        val start = edits.first().offset
        val end = (starts.getOrNull(last + 1) ?: text.length)
        val replacement = buildString {
            var at = start
            edits.forEach { edit -> append(text, at, edit.offset); append(edit.inserted); at = edit.offset + edit.removed }
            append(text, at, end)
        }
        fun mapped(offset: Int): Int {
            var delta = 0
            for (edit in edits) {
                if (offset < edit.offset) break
                if (offset < edit.offset + edit.removed) return edit.offset + delta
                delta += edit.inserted.length - edit.removed
            }
            return offset + delta
        }
        return buffer.replace(start, end, replacement, selection, EditSelection(mapped(selection.start), mapped(selection.end)))
    }
}
