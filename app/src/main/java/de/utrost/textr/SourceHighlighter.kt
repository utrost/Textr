package de.utrost.textr

import java.util.Locale

/** Lightweight source decoration, deliberately not a compiler or an interpreter. */
object SourceHighlighter {
    val kinds = setOf(FileKind.JAVA, FileKind.JAVASCRIPT, FileKind.CSS, FileKind.KOTLIN,
        FileKind.PYTHON, FileKind.SHELL, FileKind.SQL, FileKind.DIFF, FileKind.ICALENDAR)
    private val slashComments = setOf(FileKind.JAVA, FileKind.JAVASCRIPT, FileKind.KOTLIN, FileKind.CSS)
    private val hashComments = setOf(FileKind.PYTHON, FileKind.SHELL)
    private val backticks = setOf(FileKind.JAVASCRIPT, FileKind.SHELL)
    private val tripleStrings = setOf(FileKind.JAVA, FileKind.KOTLIN, FileKind.PYTHON)
    private val keywords = mapOf(
        FileKind.JAVA to "abstract assert boolean break byte case catch char class const continue default do double else enum extends final finally float for if implements import instanceof int interface long native new package private protected public record return sealed short static strictfp super switch synchronized this throw throws transient try var void volatile while yield true false null",
        FileKind.JAVASCRIPT to "async await break case catch class const continue debugger default delete do else export extends false finally for from function get if import in instanceof let new null of return set static super switch this throw true try typeof undefined var void while with yield",
        FileKind.KOTLIN to "abstract actual annotation as break by catch class companion const constructor continue crossinline data delegate do dynamic else enum expect external false field file final finally for fun get if import in infix init inline inner interface internal is lateinit noinline null object open operator out override package param private property protected public receiver reified return sealed set setparam super suspend tailrec this throw true try typealias typeof val var vararg when where while",
        FileKind.PYTHON to "False None True and as assert async await break class continue def del elif else except finally for from global if import in is lambda nonlocal not or pass raise return try while with yield match case",
        FileKind.SHELL to "if then else elif fi case esac for select while until do done in function time coproc export local readonly return break continue declare typeset",
        FileKind.SQL to "select from where insert into values update set delete create alter drop table index view join inner outer left right full cross on as and or not null true false is in exists between like distinct order by group having limit offset union all with recursive case when then else end primary foreign key references constraint default begin commit rollback grant revoke"
    ).mapValues { (_, value) -> value.split(' ').toSet() }

    fun highlight(text: String, kind: FileKind): String = buildString(text.length) {
        var index = 0
        var tokens = 0
        var braces = 0
        while (index < text.length) {
            RenderPolicy.checkCancellation()
            if (tokens >= RenderPolicy.MAX_HIGHLIGHT_TOKENS - if (kind == FileKind.ICALENDAR) 2 else 0) {
                append(SyntaxHighlighter.escape(text.substring(index)))
                break
            }
            val start = index
            val c = text[index]
            var style: String? = null
            fun lineEnd(from: Int): Int {
                var end = from
                while (end < text.length && text[end] != '\n' && text[end] != '\r') end++
                return end
            }
            when {
                kind == FileKind.DIFF -> {
                    index = lineEnd(index)
                    if (index == start) index++
                    style = when {
                        text.startsWith("+++", start) || text.startsWith("---", start) || text.startsWith("@@", start) || text.startsWith("diff ", start) -> "key"
                        c == '+' -> "addition"
                        c == '-' -> "deletion"
                        else -> null
                    }
                }
                kind == FileKind.ICALENDAR -> {
                    val end = lineEnd(index)
                    if (end == index) index++ else {
                        var quoted = false
                        while (index < end) {
                            val next = text[index++]
                            if (next == '"') quoted = !quoted
                            if (next == ':' && !quoted) break
                        }
                        // Continuations retain their original whitespace and text.
                        if (c == ' ' || c == '\t') index = end
                        val semicolon = text.indexOf(';', start).takeIf { it in start until index && c != ' ' && c != '\t' } ?: index
                        fun span(from: Int, until: Int, tokenStyle: String) {
                            if (from < until) {
                                append("<span class=\"").append(tokenStyle).append("\">")
                                append(SyntaxHighlighter.escape(text.substring(from, until))).append("</span>")
                                tokens++
                            }
                        }
                        span(start, semicolon, if (c == ' ' || c == '\t') "string" else "key")
                        span(semicolon, index, "literal")
                        span(index, end, "string")
                        index = end
                        continue
                    }
                }
                (kind in slashComments || kind == FileKind.SQL) && text.startsWith("/*", index) -> {
                    index = text.indexOf("*/", index + 2).let { if (it < 0) text.length else it + 2 }
                    style = "comment"
                }
                (kind in slashComments && kind != FileKind.CSS && text.startsWith("//", index)) ||
                    (kind == FileKind.SQL && text.startsWith("--", index)) ||
                    (kind in hashComments && c == '#') -> {
                    index = lineEnd(index); style = "comment"
                }
                c == '\'' || c == '"' || (kind in backticks && c == '`') -> {
                    val triple = kind in tripleStrings && text.startsWith(c.toString().repeat(3), index)
                    val delimiter = c.toString().repeat(if (triple) 3 else 1)
                    index += delimiter.length
                    while (index < text.length) {
                        if (text[index] == '\\' && kind != FileKind.SQL) { index = minOf(index + 2, text.length); continue }
                        if (text.startsWith(delimiter, index)) {
                            index += delimiter.length
                            if (kind == FileKind.SQL && text.getOrNull(index) == c) { index++; continue }
                            break
                        }
                        index++
                    }
                    style = "string"
                }
                c.isLetterOrDigit() || c in "_@$-#." -> {
                    index++
                    while (index < text.length && (text[index].isLetterOrDigit() || text[index] in "_$" || (kind == FileKind.CSS && text[index] == '-'))) index++
                    val word = text.substring(start, index)
                    var after = index
                    while (after < text.length && text[after] in " \t") after++
                    style = when {
                        (if (kind == FileKind.SQL) word.lowercase(Locale.ROOT) else word) in keywords[kind].orEmpty() -> "literal"
                        c.isDigit() -> "number"
                        kind == FileKind.CSS && (braces == 0 || text.getOrNull(after) == ':' || c == '@') -> "key"
                        kind == FileKind.CSS && c == '#' -> "number"
                        else -> null
                    }
                }
                else -> { index++; if (c == '{') braces++; if (c == '}') braces = (braces - 1).coerceAtLeast(0) }
            }
            if (style != null) { append("<span class=\"").append(style).append("\">"); tokens++ }
            append(SyntaxHighlighter.escape(text.substring(start, index)))
            if (style != null) append("</span>")
        }
    }
}
