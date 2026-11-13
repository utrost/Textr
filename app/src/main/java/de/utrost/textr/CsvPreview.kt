package de.utrost.textr

data class CsvOptions(val delimiter: Char = ',', val firstRowHeader: Boolean = false) {
    init { require(delimiter in DELIMITERS) }
    companion object { val DELIMITERS = listOf(',', ';', '\t', '|') }
}

/** Strict, bounded CSV. Never evaluates cells, guesses delimiters or discards ragged rows. */
object CsvPreview {
    const val MAX_CHARS = 256 * 1024
    const val MAX_ROWS = 1000
    const val MAX_COLUMNS = 100
    const val MAX_CELLS = 10_000
    const val MAX_FIELD = 16_384

    fun parse(source: String, delimiter: Char = ','): List<List<String>> {
        require(delimiter in CsvOptions.DELIMITERS && source.length <= MAX_CHARS)
        if (source.isEmpty()) return emptyList()
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var closedQuote = false
        var cells = 0
        fun cell() {
            require(row.size < MAX_COLUMNS && ++cells <= MAX_CELLS)
            row.add(field.toString()); field.setLength(0); closedQuote = false
        }
        fun record() {
            cell(); require(rows.size < MAX_ROWS)
            rows.add(row); row = mutableListOf()
        }
        var index = 0
        while (index < source.length) {
            RenderPolicy.checkCancellation()
            val c = source[index++]
            when {
                quoted -> if (c == '"') {
                    if (source.getOrNull(index) == '"') { field.append('"'); index++ }
                    else { quoted = false; closedQuote = true }
                } else field.append(c)
                c == delimiter -> cell()
                c == '\r' || c == '\n' -> {
                    if (c == '\r' && source.getOrNull(index) == '\n') index++
                    record()
                }
                closedQuote -> error("Unexpected character after closing quote")
                c == '"' -> { require(field.isEmpty()); quoted = true }
                else -> field.append(c)
            }
            require(field.length <= MAX_FIELD)
        }
        require(!quoted)
        if (field.isNotEmpty() || closedQuote || row.isNotEmpty() || source.last() == delimiter) record()
        return rows
    }

    fun render(source: String, options: CsvOptions): String = buildString {
        val rows = parse(source, options.delimiter)
        val columns = rows.maxOfOrNull { it.size } ?: 0
        append("<div class=\"csv-scroll\"><table class=\"csv\"><thead><tr>")
        // Generated headings are explicit; the first source row remains data by default.
        for (index in 0 until columns) {
            val label = if (options.firstRowHeader) rows.first().getOrNull(index).orEmpty() else "Column ${index + 1}"
            append("<th scope=\"col\">").append(SyntaxHighlighter.escape(label)).append("</th>")
        }
        append("</tr></thead><tbody>")
        rows.drop(if (options.firstRowHeader) 1 else 0).forEach { row ->
            RenderPolicy.checkCancellation()
            append("<tr>")
            // Do not pad ragged rows: absent fields and explicit empty fields stay distinct.
            row.forEach { append("<td>").append(SyntaxHighlighter.escape(it)).append("</td>") }
            append("</tr>")
        }
        append("</tbody></table></div>")
    }
}
