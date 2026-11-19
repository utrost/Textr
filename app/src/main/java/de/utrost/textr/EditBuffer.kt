package de.utrost.textr

import java.security.MessageDigest

internal fun textFingerprint(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    for (char in value) { digest.update((char.code ushr 8).toByte()); digest.update(char.code.toByte()) }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

data class EditSelection(val start: Int, val end: Int = start)
data class EditChange(val start: Int, val before: String, val after: String,
    val selectionBefore: EditSelection, val selectionAfter: EditSelection)
data class LiteralOptions(val caseSensitive: Boolean = false, val wholeWord: Boolean = false)
data class LiteralMatches(val ranges: List<IntRange>, val truncated: Boolean)

/** Native editor backing state. Changes are bounded deltas, never full-buffer undo snapshots. */
class EditBuffer(initial: String, private var baseline: String = textFingerprint(initial)) {
    companion object {
        const val MAX_CHARS = 256 * 1024
        const val MAX_REPLACEMENT = 4096
        const val MAX_LINE_CHARS = 4096
        const val MAX_LINES = 10_000
        fun accepts(value: CharSequence): Boolean {
            if (value.length > MAX_CHARS) return false
            var lines = 1; var length = 0; var index = 0
            while (index < value.length) {
                when (value[index++]) {
                    '\r' -> { if (index < value.length && value[index] == '\n') index++; length = 0; if (++lines > MAX_LINES) return false }
                    '\n' -> { length = 0; if (++lines > MAX_LINES) return false }
                    else -> if (++length > MAX_LINE_CHARS) return false
                }
            }
            return true
        }
        const val MAX_OPERATIONS = 20; const val MAX_HISTORY_CHARS = 1024 * 1024; const val MAX_QUERY = 1000; const val MAX_MATCHES = 200 }
    init { require(accepts(initial)) }
    var text = initial; private set
    var selection = EditSelection(0); private set
    var revision = 0L; private set
    private val undo = ArrayDeque<EditChange>()
    private val redo = ArrayDeque<EditChange>()
    private var fingerprintRevision = -1L
    private var fingerprint = ""
    val dirty get(): Boolean {
        if (fingerprintRevision != revision) { fingerprint = textFingerprint(text); fingerprintRevision = revision }
        return fingerprint != baseline
    }
    val canUndo get() = undo.isNotEmpty()
    val canRedo get() = redo.isNotEmpty()
    val historyCharacters get() = (undo.asSequence() + redo.asSequence()).sumOf { it.before.length + it.after.length }
    fun select(value: EditSelection) { selection = EditSelection(value.start.coerceIn(0, text.length), value.end.coerceIn(0, text.length)) }
    fun replace(start: Int, end: Int, value: String, before: EditSelection = selection,
        after: EditSelection = EditSelection(start + value.length)): Boolean {
        require(start in 0..text.length && end in start..text.length)
        if (text.length - (end - start) + value.length > MAX_CHARS) return false
        val old = text.substring(start, end)
        if (old == value) { select(after); return true }
        val changed = text.replaceRange(start, end, value)
        if (!accepts(changed)) return false
        val change = EditChange(start, old, value, before, after)
        text = changed; select(after); revision++
        redo.clear(); undo.addLast(change)
        while (undo.size > MAX_OPERATIONS || historyCharacters > MAX_HISTORY_CHARS) undo.removeFirst()
        return true
    }
    fun undo(): Boolean {
        val change = undo.removeLastOrNull() ?: return false
        text = text.replaceRange(change.start, change.start + change.after.length, change.before)
        select(change.selectionBefore); redo.addLast(change); revision++; return true
    }
    fun redo(): Boolean {
        val change = redo.removeLastOrNull() ?: return false
        text = text.replaceRange(change.start, change.start + change.before.length, change.after)
        select(change.selectionAfter); undo.addLast(change); revision++; return true
    }
    fun touch() { revision++ }
    fun markSaved() { baseline = textFingerprint(text) }
    fun baselineFingerprint() = baseline
    fun restoreRevision(value: Long) { revision = value.coerceAtLeast(0) }
    fun matches(query: String, options: LiteralOptions, maximum: Int = MAX_MATCHES): LiteralMatches {
        require(maximum > 0)
        if (query.isEmpty() || query.length > MAX_QUERY) return LiteralMatches(emptyList(), false)
        val ranges = mutableListOf<IntRange>()
        val complete = LiteralSearch.visit(text, query, options.caseSensitive, options.wholeWord) { start, end ->
            if (ranges.size == maximum) false else { ranges.add(start until end); true }
        }
        return LiteralMatches(ranges, !complete)
    }
    fun countMatches(query: String, options: LiteralOptions): Int {
        if (query.isEmpty() || query.length > MAX_QUERY) return 0
        var count = 0
        LiteralSearch.visit(text, query, options.caseSensitive, options.wholeWord) { _, _ -> count++; true }
        return count
    }
    /** Complete non-overlapping replacement, one history operation; failure leaves all state intact. */
    fun replaceAll(query: String, replacement: String, options: LiteralOptions): Int? {
        if (query.isEmpty() || query.length > MAX_QUERY) return 0
        var count = 0; var removed = 0; var first = 0
        LiteralSearch.visit(text, query, options.caseSensitive, options.wholeWord) { start, end ->
            if (count == 0) first = start
            count++; removed += end - start; true
        }
        if (count == 0) return 0
        val length = text.length.toLong() - removed + count.toLong() * replacement.length
        if (length !in 0..MAX_CHARS.toLong()) return null
        val changed = buildString(length.toInt()) {
            var from = 0
            LiteralSearch.visit(text, query, options.caseSensitive, options.wholeWord) { start, end ->
                append(text, from, start); append(replacement); from = end; true
            }
            append(text, from, text.length)
        }
        return if (replace(0, text.length, changed, selection, EditSelection(first + replacement.length))) count else null
    }
}

/** Only newly inserted bare LF follows a uniform original convention. Existing CR/CRLF stay exact. */
fun editorInsertion(value: CharSequence, ending: String): String {
    if (ending != "CRLF" && ending != "CR") return value.toString()
    return buildString {
        value.forEachIndexed { index, char ->
            if (char == '\n' && (index == 0 || value[index - 1] != '\r')) append(if (ending == "CRLF") "\r\n" else "\r") else append(char)
        }
    }
}
