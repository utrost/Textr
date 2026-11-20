package de.utrost.textr

data class SourceSearchOptions(val query: String = "", val matchCase: Boolean = false, val wholeWord: Boolean = false)
data class SourceMatch(val start: Int, val end: Int, val line: Int, val snippet: String)
data class SourceSearchResult(val matches: List<SourceMatch>, val truncated: Boolean)

/** Literal, non-overlapping search with bounded result/snippet storage. */
object SourceSearch {
    const val MAX_QUERY = 1000
    const val MAX_RESULTS = 200
    fun find(text: String, options: SourceSearchOptions): SourceSearchResult {
        val matches = mutableListOf<SourceMatch>()
        var line = 1; var lineOffset = 0
        val complete = LiteralSearch.visit(text, options.query, options.matchCase, options.wholeWord) { start, end ->
            if (matches.size == MAX_RESULTS) false else {
                while (lineOffset < start) {
                    if (lineOffset % 1024 == 0) RenderPolicy.checkCancellation()
                    if (text[lineOffset] == '\n' || (text[lineOffset] == '\r' && text.getOrNull(lineOffset + 1) != '\n')) line++
                    lineOffset++
                }
                var from = (start - 40).coerceAtLeast(0); var to = (start + 100).coerceAtMost(text.length)
                if (from > 0 && Character.isLowSurrogate(text[from])) from--
                if (to < text.length && Character.isLowSurrogate(text[to])) to++
                val snippet = (if (from > 0) "…" else "") + text.substring(from, to).replace('\r', ' ').replace('\n', ' ').replace('\t', ' ') + if (to < text.length) "…" else ""
                matches.add(SourceMatch(start, end, line, snippet)); true
            }
        }
        return SourceSearchResult(matches, !complete)
    }
}
