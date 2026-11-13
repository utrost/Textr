package de.utrost.textr

data class SourceSearchOptions(val query: String = "", val matchCase: Boolean = false, val wholeWord: Boolean = false)
data class SourceMatch(val start: Int, val end: Int, val line: Int, val snippet: String)
data class SourceSearchResult(val matches: List<SourceMatch>, val truncated: Boolean)

/** Literal, non-overlapping search. KMP bounds work even for repetitive multi-MiB input. */
object SourceSearch {
    const val MAX_QUERY = 1000
    const val MAX_RESULTS = 200

    fun find(text: String, options: SourceSearchOptions): SourceSearchResult {
        require(options.query.length <= MAX_QUERY)
        RenderPolicy.checkCancellation()
        if (options.query.isEmpty()) return SourceSearchResult(emptyList(), false)
        fun fold(code: Int) = if (options.matchCase) code else Character.toLowerCase(Character.toUpperCase(code))
        val query = options.query.codePoints().toArray().map(::fold).toIntArray()
        val prefix = IntArray(query.size)
        for (i in 1 until query.size) {
            var j = prefix[i - 1]
            while (j > 0 && query[i] != query[j]) j = prefix[j - 1]
            if (query[i] == query[j]) j++
            prefix[i] = j
        }
        val starts = IntArray(query.size)
        val matches = mutableListOf<SourceMatch>()
        var position = 0; var processed = 0; var matched = 0; var line = 1; var lineOffset = 0
        while (position < text.length) {
            if (processed % 1024 == 0) RenderPolicy.checkCancellation()
            starts[processed % query.size] = position
            val code = text.codePointAt(position)
            val value = fold(code)
            position += Character.charCount(code)
            while (matched > 0 && value != query[matched]) matched = prefix[matched - 1]
            if (value == query[matched]) matched++
            if (matched == query.size) {
                val start = starts[(processed - query.size + 1) % query.size]
                val whole = !options.wholeWord ||
                    ((start == 0 || !word(text.codePointBefore(start))) &&
                        (position == text.length || !word(text.codePointAt(position))))
                if (whole) {
                    if (matches.size == MAX_RESULTS) return SourceSearchResult(matches, true)
                    while (lineOffset < start) {
                        if (lineOffset % 1024 == 0) RenderPolicy.checkCancellation()
                        if (text[lineOffset] == '\n' || (text[lineOffset] == '\r' && text.getOrNull(lineOffset + 1) != '\n')) line++
                        lineOffset++
                    }
                    var from = (start - 40).coerceAtLeast(0)
                    var to = (start + 100).coerceAtMost(text.length)
                    if (from > 0 && Character.isLowSurrogate(text[from])) from--
                    if (to < text.length && Character.isLowSurrogate(text[to])) to++
                    val snippet = (if (from > 0) "…" else "") +
                        text.substring(from, to).replace('\r', ' ').replace('\n', ' ').replace('\t', ' ') +
                        if (to < text.length) "…" else ""
                    matches.add(SourceMatch(start, position, line, snippet))
                    matched = 0
                } else matched = prefix[matched - 1]
            }
            processed++
        }
        return SourceSearchResult(matches, false)
    }

    private fun word(code: Int): Boolean = Character.isLetterOrDigit(code) || code == '_'.code ||
        Character.getType(code) in setOf(Character.NON_SPACING_MARK.toInt(), Character.COMBINING_SPACING_MARK.toInt(), Character.ENCLOSING_MARK.toInt())
}
