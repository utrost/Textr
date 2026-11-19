package de.utrost.textr

import java.util.Locale

/** Approximate source colors only. Never loads properties, interpolates .env, or runs builds. */
object ConfigurationHighlighter {
    val kinds = setOf(FileKind.ENV, FileKind.PROPERTIES, FileKind.DOCKERFILE)
    private val instructions = "ADD ARG CMD COPY ENTRYPOINT ENV EXPOSE FROM HEALTHCHECK LABEL MAINTAINER ONBUILD RUN SHELL STOPSIGNAL USER VOLUME WORKDIR".split(' ').toSet()
    fun highlight(source: String, kind: FileKind): String = buildString(source.length) {
        var position = 0
        var tokens = 0
        var continued = false
        var quote: Char? = null
        var dockerEscape = '\\'
        fun emit(start: Int, end: Int, style: String? = null) {
            if (start >= end) return
            val decorate = style != null && tokens < RenderPolicy.MAX_HIGHLIGHT_TOKENS
            if (decorate) { append("<span class=\"").append(style).append("\">"); tokens++ }
            append(SyntaxHighlighter.escape(source.substring(start, end)))
            if (decorate) append("</span>")
        }
        while (position < source.length) {
            RenderPolicy.checkCancellation()
            if (tokens >= RenderPolicy.MAX_HIGHLIGHT_TOKENS) { emit(position, source.length); break }
            val start = position
            var end = start
            while (end < source.length && source[end] !in "\r\n") end++
            var cursor = start
            while (cursor < end && source[cursor] in " \t\u000c") cursor++
            emit(start, cursor)
            var commentLine = false
            if (!continued && quote == null && cursor < end && (source[cursor] == '#' || (kind == FileKind.PROPERTIES && source[cursor] == '!'))) {
                if (kind == FileKind.DOCKERFILE) {
                    val directive = source.substring(cursor, end).trim().lowercase(Locale.ROOT)
                    if (directive.matches(Regex("#\\s*escape\\s*=\\s*`"))) dockerEscape = '`'
                    if (directive.matches(Regex("#\\s*escape\\s*=\\s*\\\\"))) dockerEscape = '\\'
                }
                emit(cursor, end, "comment")
                commentLine = true
                cursor = end
            } else if (!continued && quote == null) {
                when (kind) {
                    FileKind.ENV -> {
                        if (source.startsWith("export", cursor) && source.getOrNull(cursor + 6) in listOf(' ', '\t')) {
                            emit(cursor, cursor + 6, "literal"); cursor += 6
                            val whitespace = cursor
                            while (cursor < end && source[cursor] in " \t") cursor++
                            emit(whitespace, cursor)
                        }
                        val keyStart = cursor
                        if (cursor < end && (source[cursor] in 'a'..'z' || source[cursor] in 'A'..'Z' || source[cursor] == '_')) {
                            cursor++
                            while (cursor < end && (source[cursor].isLetterOrDigit() || source[cursor] == '_')) cursor++
                        }
                        emit(keyStart, cursor, "key")
                        val separator = cursor
                        while (cursor < end && source[cursor] in " \t") cursor++
                        if (source.getOrNull(cursor) == '=') cursor++
                        emit(separator, cursor, "literal")
                    }
                    FileKind.PROPERTIES -> {
                        val keyStart = cursor
                        while (cursor < end && source[cursor] !in "=: \t\u000c") {
                            cursor += if (source[cursor] == '\\' && cursor + 1 < end) 2 else 1
                        }
                        emit(keyStart, cursor, "key")
                        val separator = cursor
                        while (cursor < end && source[cursor] in " \t\u000c") cursor++
                        if (cursor < end && source[cursor] in "=:") cursor++
                        while (cursor < end && source[cursor] in " \t\u000c") cursor++
                        emit(separator, cursor, "literal")
                    }
                    FileKind.DOCKERFILE -> {
                        val wordStart = cursor
                        while (cursor < end && source[cursor].isLetter()) cursor++
                        emit(wordStart, cursor, if (source.substring(wordStart, cursor).uppercase(Locale.ROOT) in instructions) "literal" else null)
                    }
                    else -> Unit
                }
            }
            var chunk = cursor
            while (cursor < end) {
                val char = source[cursor]
                if (kind == FileKind.ENV && quote == null && char == '#') {
                    emit(chunk, cursor, "string"); emit(cursor, end, "comment"); cursor = end; chunk = end; break
                }
                if (char == (if (kind == FileKind.DOCKERFILE) dockerEscape else '\\')) {
                    emit(chunk, cursor, "string")
                    val count = if (kind == FileKind.PROPERTIES && source.getOrNull(cursor + 1) == 'u') 6 else 2
                    val until = minOf(end, cursor + count)
                    emit(cursor, until, "literal"); cursor = until; chunk = cursor; continue
                }
                if (kind == FileKind.ENV && char in "\"'") {
                    if (quote == null) quote = char else if (quote == char) quote = null
                }
                cursor++
            }
            emit(chunk, end, "string")
            var trailing = end - 1
            val escape = if (kind == FileKind.DOCKERFILE) dockerEscape else '\\'
            while (trailing >= start && source[trailing] == escape) trailing--
            continued = !commentLine && kind != FileKind.ENV && (end - 1 - trailing) % 2 == 1
            var next = end
            if (source.getOrNull(next) == '\r') next++
            if (source.getOrNull(next) == '\n') next++
            emit(end, next)
            position = next
        }
    }
}
