package de.utrost.textr

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.core.StreamReadConstraints
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler
import java.io.StringReader
import javax.xml.parsers.SAXParserFactory

/** UTF-16 source ranges, never decoded/reformatted copies. IDs are stable source-order indices. */
data class StructureNode(val id: Int, val parent: Int, val label: String, val path: String,
    val type: String, val start: Int, var end: Int, val line: Int, val depth: Int)
data class StructuredOutline(val nodes: List<StructureNode>, val available: Boolean)

object StructureParser {
    const val MAX_CHARS = 256 * 1024
    const val MAX_NODES = 2_000
    const val MAX_DEPTH = 64
    const val MAX_PATH_CHARS = 256 * 1024
    const val MAX_COPY_CHARS = 64 * 1024
    private val json = JsonFactory.builder().streamReadConstraints(StreamReadConstraints.builder()
        .maxNestingDepth(MAX_DEPTH).maxStringLength(MAX_CHARS).maxNumberLength(MAX_CHARS).build()).build()

    fun parse(source: String, kind: FileKind): StructuredOutline {
        RenderPolicy.checkCancellation()
        if (source.length > MAX_CHARS) return StructuredOutline(emptyList(), false)
        return try {
            val builder = Builder(source)
            when (kind) { FileKind.JSON -> json(source, builder); FileKind.XML -> xml(source, builder); else -> error("Unsupported") }
            require(builder.nodes.isNotEmpty())
            StructuredOutline(builder.nodes, true)
        } catch (error: Exception) {
            if (error is java.util.concurrent.CancellationException) throw error
            StructuredOutline(emptyList(), false)
        }
    }

    private class Builder(val source: String) {
        val nodes = mutableListOf<StructureNode>()
        private var paths = 0
        private val lines = buildList {
            add(0)
            source.forEachIndexed { index, char ->
                if (index % 1024 == 0) RenderPolicy.checkCancellation()
                if (char == '\n' || (char == '\r' && source.getOrNull(index + 1) != '\n')) add(index + 1)
            }
        }
        fun add(parent: Int, label: String, path: String, type: String, start: Int, end: Int, depth: Int): Int {
            RenderPolicy.checkCancellation()
            paths += path.length
            require(nodes.size < MAX_NODES && depth < MAX_DEPTH && paths <= MAX_PATH_CHARS)
            require(start in 0..source.length && end in start..source.length)
            val line = lines.binarySearch(start).let { if (it >= 0) it + 1 else -it - 1 }
            val id = nodes.size
            nodes.add(StructureNode(id, parent, label.take(160), path, type, start, end, line, depth))
            return id
        }
    }
    private data class JsonFrame(val id: Int, val objectValue: Boolean, var key: String = "", var index: Int = 0,
        val occurrences: MutableMap<String, Int> = mutableMapOf())
    private fun json(source: String, builder: Builder) {
        val stack = java.util.ArrayDeque<JsonFrame>()
        var roots = 0
        json.createParser(source).use { parser ->
            while (true) {
                RenderPolicy.checkCancellation()
                val token = parser.nextToken() ?: break
                val parent = stack.peekLast()
                if (token == JsonToken.FIELD_NAME) { require(parent?.objectValue == true); parent!!.key = parser.currentName(); continue }
                if (token == JsonToken.END_OBJECT || token == JsonToken.END_ARRAY) {
                    builder.nodes[stack.removeLast().id].end = parser.currentLocation().charOffset.toInt()
                    continue
                }
                val segment = if (parent == null) { require(roots++ == 0); "$" }
                    else if (parent.objectValue) {
                        val occurrence = (parent.occurrences[parent.key] ?: 0) + 1
                        parent.occurrences[parent.key] = occurrence
                        "[${quoted(parent.key)}]@$occurrence"
                    } else "[${parent.index++}]"
                val path = if (parent == null) segment else builder.nodes[parent.id].path + segment
                val type = when (token) {
                    JsonToken.START_OBJECT -> "object"; JsonToken.START_ARRAY -> "array"
                    JsonToken.VALUE_STRING -> "string"; JsonToken.VALUE_NUMBER_INT, JsonToken.VALUE_NUMBER_FLOAT -> "number"
                    JsonToken.VALUE_TRUE, JsonToken.VALUE_FALSE -> "boolean"; JsonToken.VALUE_NULL -> "null"; else -> error("Unexpected JSON token")
                }
                // String tokens may be lazy; finish them before recording the exact source end.
                parser.finishToken()
                val start = parser.currentTokenLocation().charOffset.toInt()
                val end = parser.currentLocation().charOffset.toInt()
                val id = builder.add(parent?.id ?: -1, if (parent?.objectValue == true) parent.key else segment,
                    path, type, start, end, stack.size)
                if (token == JsonToken.START_OBJECT || token == JsonToken.START_ARRAY)
                    stack.addLast(JsonFrame(id, token == JsonToken.START_OBJECT))
            }
        }
        require(stack.isEmpty())
    }

    /** JSON-quoted names make paths unambiguous even for quotes, slashes, braces and control characters. */
    fun quoted(value: String): String = buildString {
        append('"')
        value.forEach { char -> when (char) {
            '"' -> append("\\\""); '\\' -> append("\\\\"); '\n' -> append("\\n"); '\r' -> append("\\r"); '\t' -> append("\\t")
            else -> if (char < ' ') append("\\u%04x".format(char.code)) else append(char)
        } }
        append('"')
    }

    private data class RawAttribute(val name: String, val start: Int, val end: Int)
    private data class Tag(val name: String, val start: Int, val end: Int, val closing: Boolean, val attributes: List<RawAttribute> = emptyList())
    /** Lex only markup locations; SAX validates XML and namespaces. No declarations are accepted. */
    private fun tags(source: String): List<Tag> {
        val result = mutableListOf<Tag>()
        var at = 0
        while (at < source.length) {
            RenderPolicy.checkCancellation()
            val start = source.indexOf('<', at)
            if (start < 0) break
            fun skip(prefix: String, suffix: String): Boolean {
                if (!source.startsWith(prefix, start)) return false
                val end = source.indexOf(suffix, start + prefix.length)
                require(end >= 0); at = end + suffix.length; return true
            }
            if (skip("<!--", "-->" ) || skip("<![CDATA[", "]]>" ) || skip("<?", "?>")) continue
            require(!source.startsWith("<!", start)) // Reject DOCTYPE, ENTITY and every other declaration before SAX.
            var cursor = start + 1
            val closing = source.getOrNull(cursor) == '/'
            if (closing) cursor++
            fun name(): String {
                val begin = cursor
                while (cursor < source.length && !source[cursor].isWhitespace() && source[cursor] !in "=/>\"'") cursor++
                require(cursor > begin); return source.substring(begin, cursor)
            }
            val name = name()
            val attributes = mutableListOf<RawAttribute>()
            while (true) {
                while (source.getOrNull(cursor)?.isWhitespace() == true) cursor++
                if (source.getOrNull(cursor) in listOf('/', '>')) break
                require(!closing)
                val attribute = name()
                while (source.getOrNull(cursor)?.isWhitespace() == true) cursor++
                require(source.getOrNull(cursor++) == '=')
                while (source.getOrNull(cursor)?.isWhitespace() == true) cursor++
                val quote = source.getOrNull(cursor++)
                require(quote == '\'' || quote == '"')
                val valueStart = cursor
                val valueEnd = source.indexOf(quote!!, cursor)
                require(valueEnd >= 0)
                attributes.add(RawAttribute(attribute, valueStart, valueEnd))
                require(attributes.size + result.size < MAX_NODES * 2)
                cursor = valueEnd + 1
            }
            val selfClosing = source.getOrNull(cursor) == '/'
            if (selfClosing) cursor++
            require(source.getOrNull(cursor++) == '>')
            result.add(Tag(name, start, cursor, closing, attributes))
            if (selfClosing) result.add(Tag(name, start, cursor, true))
            require(result.size <= MAX_NODES * 2)
            at = cursor
        }
        return result
    }
    private data class XmlFrame(val id: Int, val counts: MutableMap<String, Int> = mutableMapOf())
    private fun xml(source: String, builder: Builder) {
        val tags = tags(source)
        val stack = java.util.ArrayDeque<XmlFrame>()
        var cursor = 0
        val factory = SAXParserFactory.newInstance().apply { isNamespaceAware = true; isValidating = false }
        val reader = factory.newSAXParser().xmlReader
        // Android's Expat and desktop SAX expose different feature sets. Lexing above rejects all
        // DTD/entity declarations independently; this resolver is also fail-closed for every URI.
        for (feature in listOf("http://xml.org/sax/features/external-general-entities", "http://xml.org/sax/features/external-parameter-entities", "http://apache.org/xml/features/nonvalidating/load-external-dtd")) {
            try { reader.setFeature(feature, false) } catch (_: org.xml.sax.SAXNotRecognizedException) { }
            catch (_: org.xml.sax.SAXNotSupportedException) { }
        }
        reader.entityResolver = org.xml.sax.EntityResolver { _, _ -> throw SAXException("External resources are unsupported") }
        val handler = object : DefaultHandler() {
            override fun startElement(uri: String, local: String, qualified: String, attributes: Attributes) {
                RenderPolicy.checkCancellation()
                val tag = tags[cursor++]; require(!tag.closing && tag.name == qualified)
                val parent = stack.peekLast()
                val expanded = "{${quoted(uri)}}$local"
                val number = (parent?.counts?.get(expanded) ?: 0) + 1
                parent?.counts?.set(expanded, number)
                val path = (parent?.let { builder.nodes[it.id].path } ?: "") + "/$expanded[$number]"
                val id = builder.add(parent?.id ?: -1, qualified, path, "element", tag.start, tag.end, stack.size)
                for (index in 0 until attributes.length) {
                    val raw = tag.attributes.first { it.name == attributes.getQName(index) }
                    val attributePath = "$path/@{${quoted(attributes.getURI(index))}}${attributes.getLocalName(index)}"
                    builder.add(id, "@${raw.name}", attributePath, "attribute", raw.start, raw.end, stack.size + 1)
                }
                stack.addLast(XmlFrame(id))
            }
            override fun endElement(uri: String, local: String, qualified: String) {
                RenderPolicy.checkCancellation()
                val tag = tags[cursor++]; require(tag.closing && tag.name == qualified)
                builder.nodes[stack.removeLast().id].end = tag.end
            }
            override fun characters(chars: CharArray, start: Int, length: Int) = RenderPolicy.checkCancellation()
            override fun error(error: org.xml.sax.SAXParseException) { throw error }
            override fun fatalError(error: org.xml.sax.SAXParseException) { throw error }
        }
        reader.contentHandler = handler; reader.errorHandler = handler
        reader.parse(InputSource(StringReader(source)))
        require(stack.isEmpty() && cursor == tags.size)
    }
}
