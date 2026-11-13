package de.utrost.textr

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale

/** An event summary, not a calendar engine. Never expands recurrence or resolves time zones. */
object CalendarPreview {
    const val MAX_CHARS = 512 * 1024
    const val MAX_EVENTS = 200
    const val MAX_PROPERTIES = 10_000
    const val MAX_LINE = 16_384
    const val MAX_DEPTH = 16
    const val MAX_OUTPUT = 2 * 1024 * 1024
    private val token = Regex("[A-Za-z0-9-]+")
    private val date = DateTimeFormatter.ofPattern("uuuuMMdd", Locale.ROOT).withResolverStyle(ResolverStyle.STRICT)
    private val timestamp = DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss", Locale.ROOT).withResolverStyle(ResolverStyle.STRICT)
    private val readableTime = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss", Locale.ROOT)
    private data class Property(val name: String, val parameters: Map<String, String>, val value: String)

    fun render(source: String): String {
        require(source.length <= MAX_CHARS)
        val events = mutableListOf<List<Property>>()
        val stack = mutableListOf<String>()
        var event: MutableList<Property>? = null
        var count = 0
        var calendars = 0
        var version = false
        var method: String? = null
        for (line in unfolded(source)) {
            RenderPolicy.checkCancellation()
            require(++count <= MAX_PROPERTIES)
            val property = parse(line)
            when (property.name) {
                "BEGIN" -> {
                    val component = property.value.uppercase(Locale.ROOT)
                    require(property.parameters.isEmpty() && token.matches(component))
                    if (stack.isEmpty()) { require(component == "VCALENDAR" && ++calendars == 1) }
                    else require(component != "VCALENDAR")
                    if (component == "VEVENT") {
                        require(stack == listOf("VCALENDAR") && events.size < MAX_EVENTS)
                        event = mutableListOf()
                    }
                    stack.add(component)
                    require(stack.size <= MAX_DEPTH)
                }
                "END" -> {
                    require(property.parameters.isEmpty() && stack.isNotEmpty() && stack.last() == property.value.uppercase(Locale.ROOT))
                    if (stack.last() == "VEVENT") { events.add(requireNotNull(event)); event = null }
                    stack.removeAt(stack.lastIndex)
                }
                else -> {
                    require(stack.isNotEmpty())
                    if (stack == listOf("VCALENDAR")) {
                        if (property.name == "VERSION") { require(!version && property.value == "2.0"); version = true }
                        if (property.name == "METHOD") { require(method == null); method = property.value }
                    }
                    // Alarm fields and timezone definitions are never mistaken for event fields.
                    if (stack == listOf("VCALENDAR", "VEVENT")) event!!.add(property)
                }
            }
        }
        require(stack.isEmpty() && calendars == 1 && version && events.isNotEmpty())
        return buildString {
            append("<h1>Calendar events</h1><p>Event summary. Times keep their supplied time zone; no conversion is applied. Recurrences are not expanded. Other calendar data remains in source.</p>")
            fun field(label: String, value: String) {
                append("<p><strong>").append(SyntaxHighlighter.escape(label)).append(":</strong><br><span class=\"calendar-value\">")
                append(SyntaxHighlighter.escape(value)).append("</span></p>")
                require(length <= MAX_OUTPUT)
            }
            method?.let { field("Calendar method", it) }
            events.forEach { properties ->
                RenderPolicy.checkCancellation()
                val grouped = properties.groupBy { it.name }
                for (single in listOf("SUMMARY", "DTSTART", "DTEND", "DURATION", "LOCATION", "DESCRIPTION", "STATUS", "RECURRENCE-ID")) {
                    require(grouped[single].orEmpty().size <= 1)
                }
                require(!(grouped.containsKey("DTEND") && grouped.containsKey("DURATION")))
                val start = grouped["DTSTART"]?.single()
                val end = grouped["DTEND"]?.single()
                if (start != null && end != null) {
                    require((start.parameters["VALUE"] ?: "DATE-TIME").uppercase(Locale.ROOT) == (end.parameters["VALUE"] ?: "DATE-TIME").uppercase(Locale.ROOT))
                    if (start.parameters["TZID"] == end.parameters["TZID"] && start.value.length == end.value.length) require(end.value > start.value)
                }
                val title = grouped["SUMMARY"]?.single()?.value?.let(::text).orEmpty().ifBlank { "Untitled event" }
                append("<h2>").append(SyntaxHighlighter.escape(title)).append("</h2>")
                field("Start", start?.let(::time) ?: "Not supplied")
                end?.let { field("End (exclusive)", time(it)) }
                for (property in properties) {
                    when (property.name) {
                        "LOCATION" -> field("Location", text(property.value))
                        "DESCRIPTION" -> field("Description", text(property.value))
                        "STATUS" -> field("Status", property.value)
                        "ORGANIZER" -> field("Organizer", property.value)
                        "DURATION" -> field("Duration (as supplied)", property.value)
                        "RRULE" -> field("Recurrence rule (not expanded)", property.value)
                        "RDATE" -> field("Additional recurrence dates (as supplied)", raw(property))
                        "EXDATE" -> field("Excluded dates (as supplied)", raw(property))
                        "RECURRENCE-ID" -> {
                            field("Recurrence instance", time(property))
                            property.parameters["RANGE"]?.let { field("Recurrence range", it) }
                        }
                        "URL" -> field("URL", property.value)
                    }
                }
                require(length <= MAX_OUTPUT)
            }
        }
    }

    private fun raw(property: Property): String = property.parameters.entries.joinToString("; ") { "${it.key}=${it.value}" }
        .let { if (it.isEmpty()) property.value else "$it: ${property.value}" }

    private fun time(property: Property): String {
        val valueType = property.parameters["VALUE"]?.uppercase(Locale.ROOT) ?: "DATE-TIME"
        val zone = property.parameters["TZID"]
        if (valueType == "DATE") {
            require(zone == null && Regex("[0-9]{8}").matches(property.value))
            return "${LocalDate.parse(property.value, date)} (all day)"
        }
        require(valueType == "DATE-TIME" && Regex("[0-9]{8}T[0-9]{6}Z?").matches(property.value))
        val utc = property.value.endsWith('Z')
        require(!utc || zone == null)
        val parsed = LocalDateTime.parse(property.value.removeSuffix("Z"), timestamp)
        val label = if (utc) "UTC" else zone?.let { "time zone: $it" } ?: "floating time; no time zone supplied"
        return "${parsed.format(readableTime)} ($label)"
    }

    private fun unfolded(source: String): Sequence<String> = sequence {
        var line = StringBuilder()
        for (physical in source.lineSequence()) {
            RenderPolicy.checkCancellation()
            if (physical.startsWith(' ') || physical.startsWith('\t')) {
                require(line.isNotEmpty())
                line.append(physical.substring(1))
            } else {
                if (line.isNotEmpty()) yield(line.toString())
                line = StringBuilder(physical)
            }
            require(line.length <= MAX_LINE)
        }
        if (line.isNotEmpty()) yield(line.toString())
    }

    private fun parse(line: String): Property {
        var quoted = false
        var colon = -1
        for (index in line.indices) {
            if (line[index] == '"') quoted = !quoted
            if (line[index] == ':' && !quoted) { colon = index; break }
        }
        require(colon > 0 && !quoted)
        val parts = mutableListOf<String>()
        var start = 0
        for (index in 0 until colon) {
            if (line[index] == '"') quoted = !quoted
            if (line[index] == ';' && !quoted) { parts.add(line.substring(start, index)); start = index + 1 }
        }
        parts.add(line.substring(start, colon))
        require(token.matches(parts[0]))
        val parameters = mutableMapOf<String, String>()
        for (part in parts.drop(1)) {
            val equal = part.indexOf('=')
            require(equal > 0)
            val name = part.substring(0, equal).uppercase(Locale.ROOT)
            require(token.matches(name) && name !in parameters)
            val value = part.substring(equal + 1)
            require(value.isNotEmpty())
            val unquoted = if (value.startsWith('"')) {
                require(value.length >= 2 && value.endsWith('"')); value.substring(1, value.length - 1)
            } else value
            // RFC 6868 parameter escaping, decoded in one pass.
            parameters[name] = buildString {
                var index = 0
                while (index < unquoted.length) {
                    val c = unquoted[index++]
                    if (c == '^' && index < unquoted.length) {
                        when (unquoted[index]) {
                            '^' -> { append('^'); index++ }
                            'n' -> { append('\n'); index++ }
                            '\'' -> { append('"'); index++ }
                            else -> append(c)
                        }
                    } else append(c)
                }
            }
        }
        return Property(parts[0].uppercase(Locale.ROOT), parameters, line.substring(colon + 1))
    }

    private fun text(value: String): String = buildString {
        var index = 0
        while (index < value.length) {
            val c = value[index++]
            if (c == '\\' && index < value.length) {
                when (val next = value[index++]) {
                    'n', 'N' -> append('\n')
                    ',', ';', '\\' -> append(next)
                    else -> { append(c); append(next) }
                }
            } else append(c)
        }
    }
}
