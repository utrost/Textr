package de.utrost.textr

import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.parser.Parser
import java.io.StringReader
import java.net.URI

/** Offline HTML -> Markdown, never original HTML -> WebView. No URL/base resolution. */
object HtmlMarkdown {
    const val MAX_CHARS = 256 * 1024
    const val MAX_NODES = 20_000
    const val MAX_DEPTH = 64
    private val whitespace = Regex("[\\s\\u00a0]+")
    private val codeBreaks = Regex("[\\r\\n]+")
    private val backticks = Regex("`+")
    private val cellBreaks = Regex("\\s*\n\\s*")
    private val omitted = setOf("head", "script", "style", "template", "noscript", "iframe", "object", "embed",
        "form", "input", "select", "textarea", "button", "canvas", "svg", "math", "audio", "video", "source")
    private val blocks = setOf("p", "div", "section", "article", "main", "nav", "aside", "header", "footer",
        "figure", "figcaption", "address", "dl", "dt", "dd", "details", "summary")

    fun convert(source: String, translate: (String) -> String = { it }): String {
        RenderPolicy.checkCancellation()
        require(source.length <= MAX_CHARS)
        val reader = object : StringReader(source) {
            override fun read(buffer: CharArray, off: Int, len: Int): Int {
                RenderPolicy.checkCancellation()
                return super.read(buffer, off, minOf(len, 2048))
            }
        }
        // Parsing a bounded local string cannot fetch resources. The default parser
        // depth cap is additional protection; our stricter DOM budget rejects it first.
        val document = reader.use { Parser.htmlParser().parseInput(it, "") }
        val pending = ArrayDeque<Pair<Node, Int>>()
        pending.add(document to 0)
        var count = 0
        while (pending.isNotEmpty()) {
            RenderPolicy.checkCancellation()
            val (node, depth) = pending.removeLast()
            require(++count <= MAX_NODES && depth <= MAX_DEPTH)
            for (child in node.childNodes()) pending.add(child to depth + 1)
        }
        return Converter(translate).children(document.body()).trim().also { require(it.isNotBlank()) }
    }

    private class Converter(private val translate: (String) -> String) {
        private fun bounded(value: String): String {
            require(value.length <= RenderPolicy.MAX_MARKDOWN_CHARS)
            return value
        }

        private fun join(values: Sequence<String>, separator: String = ""): String {
            val result = StringBuilder()
            for (value in values) {
                RenderPolicy.checkCancellation()
                val gap = if (result.isNotEmpty()) separator else ""
                require(result.length + gap.length + value.length <= RenderPolicy.MAX_MARKDOWN_CHARS)
                result.append(gap).append(value)
            }
            return result.toString()
        }

        fun children(node: Node, cell: Boolean = false): String =
            join(node.childNodes().asSequence().map { render(it, cell) })

        private fun escape(value: String): String = bounded(buildString {
            for (c in value) {
                RenderPolicy.checkCancellation()
                when (c) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    in "\\`*_{}[]()#+-.!|" -> append('\\').append(c)
                    else -> append(c)
                }
                require(length <= RenderPolicy.MAX_MARKDOWN_CHARS)
            }
        })

        private fun rawText(node: Node): String {
            val pending = ArrayDeque<Node>()
            pending.add(node)
            val text = StringBuilder()
            while (pending.isNotEmpty()) {
                RenderPolicy.checkCancellation()
                val current = pending.removeLast()
                if (current is TextNode) text.append(current.wholeText)
                else if (current is Element && current.normalName() == "br") text.append('\n')
                else if (current !is Element || (current.normalName() !in omitted && !current.hasAttr("hidden") && !current.attr("aria-hidden").equals("true", true))) {
                    for (child in current.childNodes().asReversed()) pending.add(child)
                }
            }
            return text.toString()
        }

        private fun fence(text: String, minimum: Int): String =
            "`".repeat(maxOf(minimum, (backticks.findAll(text).maxOfOrNull { it.value.length } ?: 0) + 1))

        private fun safeLink(value: String): String? = try {
            val uri = URI(value)
            val allowed = when (uri.scheme?.lowercase(java.util.Locale.ROOT)) {
                "http", "https" -> !uri.host.isNullOrEmpty()
                "mailto" -> uri.rawSchemeSpecificPart.isNotBlank()
                else -> false
            }
            if (allowed) uri.toASCIIString().replace("(", "%28").replace(")", "%29") else null
        } catch (_: java.net.URISyntaxException) { null }

        private fun emphasis(value: String, marker: String): String = if (value.isBlank()) value else
            value.takeWhile(Char::isWhitespace) + marker + value.trim() + marker + value.takeLastWhile(Char::isWhitespace)

        private fun render(node: Node, cell: Boolean): String {
            RenderPolicy.checkCancellation()
            if (node is TextNode) return escape(node.wholeText.replace(whitespace, " "))
            if (node !is Element) return ""
            val tag = node.normalName()
            if (tag in omitted || node.hasAttr("hidden") || node.attr("aria-hidden").equals("true", true)) return ""
            return bounded(when {
                tag == "img" -> escape(translate("[image omitted]"))
                tag == "br" -> "  \n"
                tag == "hr" -> "\n\n---\n\n"
                tag == "pre" -> {
                    val text = rawText(node)
                    val ticks = fence(text, 3)
                    "\n\n$ticks\n$text${if (text.endsWith('\n')) "" else "\n"}$ticks\n\n"
                }
                tag == "code" -> {
                    val text = rawText(node).replace(codeBreaks, " ").let { if (cell) it.replace("|", "\\|") else it }
                    val ticks = fence(text, 1)
                    // Padding protects boundary backticks/spaces without changing code contents.
                    val padding = if (text.all { it == ' ' }) "" else " "
                    if (text.isEmpty()) "" else "$ticks$padding$text$padding$ticks"
                }
                tag == "a" -> {
                    val label = children(node, cell)
                    safeLink(node.attr("href"))?.let { "[$label](<$it>)" } ?: label
                }
                tag == "strong" || tag == "b" -> emphasis(children(node, cell), "**")
                tag == "em" || tag == "i" -> emphasis(children(node, cell), "*")
                tag.length == 2 && tag[0] == 'h' && tag[1] in '1'..'6' -> "\n\n${"#".repeat(tag[1].digitToInt())} ${children(node, cell).trim()}\n\n"
                tag == "blockquote" -> "\n\n" + children(node, cell).trim().lineSequence().joinToString("\n") { "> $it" } + "\n\n"
                tag == "ul" || tag == "ol" -> list(node, cell)
                tag == "table" -> { require(!cell); table(node) }
                tag in blocks -> "\n\n${children(node, cell).trim()}\n\n"
                else -> children(node, cell)
            })
        }

        private fun list(node: Element, cell: Boolean): String {
            var index = node.attr("start").toIntOrNull()?.coerceIn(1, 999_999) ?: 1
            return "\n\n" + join(node.children().asSequence().filter { it.normalName() == "li" }.map {
                val prefix = if (node.normalName() == "ol") "${index++}. " else "- "
                val lines = children(it, cell).trim().lines()
                prefix + lines.first() + lines.drop(1).joinToString("") { line -> "\n${" ".repeat(prefix.length)}$line" }
            }, "\n") + "\n\n"
        }

        private fun table(node: Element): String {
            val rows = node.children().flatMap {
                when (it.normalName()) {
                    "tr" -> listOf(it)
                    "thead", "tbody", "tfoot" -> it.children().filter { child -> child.normalName() == "tr" }
                    "caption", "colgroup" -> emptyList()
                    else -> error("Unsupported table structure")
                }
            }
            require(rows.isNotEmpty() && rows.size <= 100)
            val width = rows.first().childrenSize()
            require(width in 1..32 && rows.size * width <= 1000)
            val values = rows.map { row ->
                require(row.childrenSize() == width)
                row.children().map { item ->
                    require(item.normalName() in setOf("td", "th"))
                    require(item.attr("colspan").let { it.isEmpty() || it == "1" })
                    require(item.attr("rowspan").let { it.isEmpty() || it == "1" })
                    require(item.getElementsByTag("pre").isEmpty())
                    children(item, true).trim().replace(cellBreaks, " ")
                }
            }
            fun row(cells: List<String>) = "| " + cells.joinToString(" | ") + " |\n"
            val hasHeader = rows.first().children().all { it.normalName() == "th" }
            val caption = node.children().filter { it.normalName() == "caption" }.asSequence().map { children(it) }
            return "\n\n" + join(caption) + "\n\n" +
                row(if (hasHeader) values.first() else List(width) { "" }) + row(List(width) { "---" }) +
                join(values.drop(if (hasHeader) 1 else 0).asSequence().map(::row)) + "\n"
        }
    }
}
