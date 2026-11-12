package de.utrost.textr

/** A bounded, single-pass lexer. It decorates source; it never rewrites it. */
object SyntaxHighlighter {
    private val configKinds = setOf(FileKind.INI, FileKind.YAML, FileKind.TOML)
    private val literals = setOf("true", "false", "null", "yes", "no")
    fun escape(text: String): String = buildString(text.length) { appendEscaped(text, 0, text.length) }

    private fun StringBuilder.appendEscaped(text: String, start: Int, end: Int) {
        for (i in start until end) {
            if (i % 4096 == 0) RenderPolicy.checkCancellation()
            when (val c = text[i]) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(c)
            }
        }
    }

    fun highlight(text: String, kind: FileKind): String {
        if (text.length > RenderPolicy.MAX_HIGHLIGHT_CHARS) return escape(text)
        if (kind == FileKind.TEXT) return escape(text)
        return buildString(text.length) {
            var index = 0
            var tokens = 0
            while (index < text.length) {
                if (index % 4096 == 0 && Thread.currentThread().isInterrupted) {
                    throw java.util.concurrent.CancellationException()
                }
                // Avoid generating millions of DOM nodes for pathological inputs.
                if (tokens >= RenderPolicy.MAX_HIGHLIGHT_TOKENS) {
                    appendEscaped(text, index, text.length)
                    break
                }
                val start = index
                val c = text[index]
                var style: String? = null
                when {
                    kind == FileKind.XML && text.startsWith("<!--", index) -> {
                        index = text.indexOf("-->", index + 4).let { if (it < 0) text.length else it + 3 }
                        style = "comment"
                    }
                    kind == FileKind.XML && c == '<' -> {
                        var quote: Char? = null
                        index++
                        while (index < text.length) {
                            val next = text[index++]
                            if (quote != null) { if (next == quote) quote = null }
                            else if (next == '\'' || next == '"') quote = next
                            else if (next == '>') break
                        }
                        style = "key"
                    }
                    kind in configKinds &&
                        (c == '#' || (kind == FileKind.INI && c == ';')) -> {
                        index = text.indexOf('\n', index).let { if (it < 0) text.length else it }
                        style = "comment"
                    }
                    kind == FileKind.MARKDOWN && (index == 0 || text[index - 1] == '\n') && c in "#>" -> {
                        index = text.indexOf('\n', index).let { if (it < 0) text.length else it }
                        style = "key"
                    }
                    c == '"' || c == '\'' || (kind == FileKind.MARKDOWN && c == '`') -> {
                        index++
                        while (index < text.length) {
                            val next = text[index++]
                            if (next == '\\' && index < text.length) index++
                            else if (next == c) break
                        }
                        var after = index
                        while (after < text.length && text[after].isWhitespace()) after++
                        style = if (after < text.length && text[after] == ':') "key" else "string"
                    }
                    c.isLetterOrDigit() || c in "_-" -> {
                        index++
                        while (index < text.length && (text[index].isLetterOrDigit() || text[index] in "_.+-")) index++
                        val word = text.substring(start, index)
                        var after = index
                        while (after < text.length && text[after] in " \t") after++
                        style = when {
                            after < text.length && text[after] in ":=" -> "key"
                            word in literals -> "literal"
                            c.isDigit() || (c == '-' && word.getOrNull(1)?.isDigit() == true) -> "number"
                            else -> null
                        }
                    }
                    else -> index++
                }
                if (style != null) {
                    append("<span class=\"").append(style).append("\">")
                    tokens++
                }
                appendEscaped(text, start, index)
                if (style != null) append("</span>")
            }
        }
    }
}
