package de.utrost.textr

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.util.Locale
import java.util.concurrent.CancellationException

const val MAX_FILE_BYTES = 5 * 1024 * 1024

enum class FileKind { TEXT, MARKDOWN, JSON, INI, YAML, TOML, XML, JAVA, JAVASCRIPT, CSS, HTML, KOTLIN, PYTHON, SHELL, SQL, DIFF, ICALENDAR }
enum class DisplayMode { PLAIN, HIGHLIGHTED, PREVIEW }
enum class ReadFailure { TOO_LARGE, BINARY, ENCODING, ACCESS, SOURCE }
class DocumentException(val reason: ReadFailure) : Exception(reason.name)

data class TextDocument(val name: String, val text: String, val kind: FileKind, val bytes: Int)

object TextDocuments {
    fun read(input: InputStream, name: String, mimeType: String? = null): TextDocument {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            if (Thread.currentThread().isInterrupted) throw CancellationException()
            // Read at most one byte past the limit, even if a provider lies about its size.
            val count = input.read(buffer, 0, minOf(buffer.size, MAX_FILE_BYTES + 1 - output.size()))
            if (count == -1) break
            if (count == 0) continue
            output.write(buffer, 0, count)
            if (output.size() > MAX_FILE_BYTES) throw DocumentException(ReadFailure.TOO_LARGE)
        }
        val bytes = output.toByteArray()
        val text = decode(bytes)
        return TextDocument(name, text, kind(name, mimeType), bytes.size)
    }

    private fun decode(bytes: ByteArray): String {
        val (charset, offset) = when {
            bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() -> Charsets.UTF_8 to 3
            bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> Charsets.UTF_16LE to 2
            bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> Charsets.UTF_16BE to 2
            else -> Charsets.UTF_8 to 0
        }
        if (charset == Charsets.UTF_8 && bytes.any { it == 0.toByte() }) {
            throw DocumentException(ReadFailure.BINARY)
        }
        val text = try {
            charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes, offset, bytes.size - offset)).toString()
        } catch (_: CharacterCodingException) {
            throw DocumentException(ReadFailure.ENCODING)
        }
        if (text.any { it.code in 0..8 || it.code in 14..31 || it.code == 127 }) {
            throw DocumentException(ReadFailure.BINARY)
        }
        return text
    }

    fun kind(name: String, mime: String?): FileKind {
        val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return when (extension) {
            "md", "markdown", "mdown" -> FileKind.MARKDOWN
            "json", "jsonld" -> FileKind.JSON
            "ini" -> FileKind.INI
            "yaml", "yml" -> FileKind.YAML
            "toml" -> FileKind.TOML
            "xml" -> FileKind.XML
            "java" -> FileKind.JAVA
            "js", "mjs", "cjs" -> FileKind.JAVASCRIPT
            "css" -> FileKind.CSS
            "html", "htm", "xhtml" -> FileKind.HTML
            "kt", "kts" -> FileKind.KOTLIN
            "py", "pyw" -> FileKind.PYTHON
            "sh", "bash" -> FileKind.SHELL
            "sql" -> FileKind.SQL
            "diff", "patch" -> FileKind.DIFF
            "ics", "ical" -> FileKind.ICALENDAR
            "txt", "log", "csv" -> FileKind.TEXT
            else -> when (mime?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)) {
                "text/markdown", "text/x-markdown" -> FileKind.MARKDOWN
                "application/json", "application/ld+json" -> FileKind.JSON
                "application/xml", "text/xml" -> FileKind.XML
                "application/yaml", "application/x-yaml", "text/yaml", "text/x-yaml" -> FileKind.YAML
                "application/toml", "application/x-toml", "text/toml" -> FileKind.TOML
                "application/ini", "application/x-ini", "text/x-ini" -> FileKind.INI
                "text/x-java-source", "text/x-java", "application/x-java-source" -> FileKind.JAVA
                "text/javascript", "application/javascript", "application/x-javascript", "text/ecmascript", "application/ecmascript" -> FileKind.JAVASCRIPT
                "text/css" -> FileKind.CSS
                "text/html", "application/xhtml+xml" -> FileKind.HTML
                "text/x-kotlin" -> FileKind.KOTLIN
                "text/x-python", "application/x-python" -> FileKind.PYTHON
                "text/x-shellscript", "application/x-sh", "application/x-shellscript" -> FileKind.SHELL
                "application/sql", "text/x-sql" -> FileKind.SQL
                "text/x-diff", "text/x-patch" -> FileKind.DIFF
                "text/calendar" -> FileKind.ICALENDAR
                else -> FileKind.TEXT
            }
        }
    }
}
