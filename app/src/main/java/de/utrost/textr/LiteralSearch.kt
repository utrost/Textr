package de.utrost.textr

/** Linear-time, code-point-aware literal matching shared by reader and copy editor. */
object LiteralSearch {
    fun visit(text: String, queryText: String, matchCase: Boolean, wholeWord: Boolean, match: (Int, Int) -> Boolean): Boolean {
        require(queryText.length <= SourceSearch.MAX_QUERY)
        RenderPolicy.checkCancellation()
        if (queryText.isEmpty()) return true
        fun fold(code: Int) = if (matchCase) code else Character.toLowerCase(Character.toUpperCase(code))
        val query = queryText.codePoints().toArray().map(::fold).toIntArray()
        val prefix = IntArray(query.size)
        for (index in 1 until query.size) {
            var matched = prefix[index - 1]
            while (matched > 0 && query[index] != query[matched]) matched = prefix[matched - 1]
            if (query[index] == query[matched]) matched++
            prefix[index] = matched
        }
        val starts = IntArray(query.size)
        var position = 0; var processed = 0; var matched = 0
        while (position < text.length) {
            if (processed % 1024 == 0) RenderPolicy.checkCancellation()
            starts[processed % query.size] = position
            val code = text.codePointAt(position); val value = fold(code)
            position += Character.charCount(code)
            while (matched > 0 && value != query[matched]) matched = prefix[matched - 1]
            if (value == query[matched]) matched++
            if (matched == query.size) {
                val start = starts[(processed - query.size + 1) % query.size]
                val whole = !wholeWord || ((start == 0 || !word(text.codePointBefore(start))) &&
                    (position == text.length || !word(text.codePointAt(position))))
                if (whole) {
                    if (!match(start, position)) return false
                    matched = 0 // accepted matches are non-overlapping
                } else matched = prefix[matched - 1]
            }
            processed++
        }
        return true
    }
    private fun word(code: Int): Boolean = Character.isLetterOrDigit(code) || code == '_'.code ||
        Character.getType(code) in setOf(Character.NON_SPACING_MARK.toInt(), Character.COMBINING_SPACING_MARK.toInt(), Character.ENCLOSING_MARK.toInt())
}
