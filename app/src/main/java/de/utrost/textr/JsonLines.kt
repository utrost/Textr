package de.utrost.textr

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.core.StreamReadConstraints

/** Every physical line is represented, including blank and invalid records; no tree expansion. */
object JsonLines {
    const val MAX_RECORDS = 1_000
    const val MAX_RECORD_CHARS = 16_384
    const val MAX_TOKENS = 200_000
    private val json = JsonFactory.builder().streamReadConstraints(StreamReadConstraints.builder()
        .maxNestingDepth(64).maxStringLength(MAX_RECORD_CHARS).maxNumberLength(MAX_RECORD_CHARS).build()).build()
    fun parse(source: String): StructuredOutline {
        if (source.length > StructureParser.MAX_CHARS) return StructuredOutline(emptyList(), false)
        val nodes = mutableListOf(StructureNode(0, -1, "JSON Lines", "$", "records", 0, source.length, 1, 0))
        var at = 0; var line = 1; var tokens = 0
        while (at < source.length) {
            RenderPolicy.checkCancellation()
            if (nodes.size > MAX_RECORDS) return StructuredOutline(emptyList(), false)
            val start = at
            while (at < source.length && source[at] !in "\r\n") at++
            val contentEnd = at
            if (source.getOrNull(at) == '\r') at++
            if (source.getOrNull(at) == '\n') at++
            val text = source.substring(start, contentEnd)
            var type = if (text.isBlank()) "blank_record" else "record"
            if (type == "record") {
                if (text.length > MAX_RECORD_CHARS) type = "invalid_record"
                else try {
                    var roots = 0; var depth = 0
                    json.createParser(text).use { parser ->
                        while (true) {
                            RenderPolicy.checkCancellation()
                            val token = parser.nextToken() ?: break
                            if (++tokens > MAX_TOKENS) return StructuredOutline(emptyList(), false)
                            if (depth == 0) roots++
                            if (token == JsonToken.START_OBJECT || token == JsonToken.START_ARRAY) depth++
                            if (token == JsonToken.END_OBJECT || token == JsonToken.END_ARRAY) depth--
                        }
                    }
                    if (roots != 1 || depth != 0) type = "invalid_record"
                } catch (error: Exception) {
                    if (error is java.util.concurrent.CancellationException) throw error
                    type = "invalid_record"
                }
            }
            nodes.add(StructureNode(nodes.size, 0, "#$line", "line[$line]", type, start, at, line, 1))
            line++
        }
        return StructuredOutline(nodes, true)
    }
}
