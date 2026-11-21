package de.utrost.textr

/** Line-oriented source decoration only: no includes, directives, interpretation or IO. */
object MarkupHighlighter {
    fun highlight(text: String, kind: FileKind): String = buildString(text.length) {
        var at = 0; var tokens = 0
        while (at < text.length) {
            RenderPolicy.checkCancellation()
            val start = at
            while (at < text.length && text[at] !in "\r\n") at++
            val line = text.substring(start, at)
            val trimmed = line.trimStart()
            val style = when {
                tokens >= RenderPolicy.MAX_HIGHLIGHT_TOKENS -> null
                line.length > EditBuffer.MAX_LINE_CHARS -> null
                kind == FileKind.ASCIIDOC && (trimmed.startsWith("//") || trimmed == "////") -> "comment"
                kind == FileKind.ASCIIDOC && (trimmed.startsWith(":") || trimmed.startsWith("include::") || trimmed.startsWith("image::") || trimmed.startsWith("[")) -> "literal"
                kind == FileKind.ASCIIDOC && (trimmed.startsWith("= ") || trimmed.startsWith("==") || trimmed == "----" || trimmed == "...." || trimmed == "++++") -> "key"
                kind == FileKind.RESTRUCTUREDTEXT && trimmed.startsWith(".. ") -> "comment"
                kind == FileKind.RESTRUCTUREDTEXT && trimmed.length >= 3 && trimmed.all { it == trimmed.first() } && trimmed.first() in "=-~^\"#*+_" -> "key"
                kind == FileKind.RESTRUCTUREDTEXT && (trimmed.startsWith(":") || trimmed.startsWith("..")) -> "literal"
                else -> null
            }
            if (style != null) { append("<span class=\"").append(style).append("\">"); tokens++ }
            append(SyntaxHighlighter.escape(line))
            if (style != null) append("</span>")
            if (text.getOrNull(at) == '\r') { append('\r'); at++ }
            if (text.getOrNull(at) == '\n') { append('\n'); at++ }
        }
    }
}
