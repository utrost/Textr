package de.utrost.textr

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.CancellationSignal
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import java.io.FilterInputStream

data class ReadingState(val mode: DisplayMode? = null, val scroll: Int = 0, val format: FileKind? = null, val encoding: TextEncoding? = null)
data class RecentFile(val uri: String, val name: String, val openedAt: Long, val reading: ReadingState = ReadingState(), val pinned: Boolean = false)

class DocumentRepository(private val context: Context) {
    private val resolver = context.contentResolver
    private val preferences = context.getSharedPreferences("recent_files", Context.MODE_PRIVATE)

    fun read(uri: Uri, mime: String?, cancellation: CancellationSignal, encoding: TextEncoding? = null): TextDocument {
        if (uri.scheme !in setOf("content", "file")) throw DocumentException(ReadFailure.SOURCE)
        cancellation.throwIfCanceled()
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "Text file"
        if (uri.scheme == "content") {
            optionalMetadata {
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null, cancellation)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (column >= 0 && !cursor.isNull(column)) name = cursor.getString(column)
                    }
                }
            }
        }
        name = name.replace(Regex("[\\p{Cntrl}]"), " ").take(256).ifBlank { "Text file" }
        val declaredType = mime?.substringBefore(';')?.trim()?.lowercase(java.util.Locale.ROOT)?.takeIf { it.isNotBlank() }
        val type = if (declaredType == null || declaredType in setOf("*/*", "application/octet-stream", "text/plain")) {
            optionalMetadata { resolver.getType(uri) } ?: declaredType
        } else declaredType
        return withSource(uri, cancellation) { TextDocuments.read(it, name, type, encoding) }
    }

    /** Read the original bytes, including BOM and line endings, through the same cancellable path. */
    fun copySource(uri: Uri, output: java.io.OutputStream, cancellation: CancellationSignal) {
        withSource(uri, cancellation) { input ->
            val buffer = ByteArray(8192)
            var count = 0
            while (true) {
                val read = input.read(buffer, 0, minOf(buffer.size, MAX_FILE_BYTES + 1 - count))
                if (read == -1) break
                count += read
                if (count > MAX_FILE_BYTES) throw DocumentException(ReadFailure.TOO_LARGE)
                output.write(buffer, 0, read)
            }
        }
    }

    private fun <T> withSource(uri: Uri, cancellation: CancellationSignal, read: (java.io.InputStream) -> T): T {
        if (uri.scheme !in setOf("content", "file")) throw DocumentException(ReadFailure.SOURCE)
        cancellation.throwIfCanceled()
        return resolver.openAssetFileDescriptor(uri, "r", cancellation)?.use { descriptor ->
            descriptor.createInputStream().use { stream ->
                // A provider may return a pipe and pause indefinitely. Poll before each
                // read so replacing a document can cancel without waiting for its writer.
                val poll = StructPollfd().apply {
                    fd = descriptor.fileDescriptor
                    events = OsConstants.POLLIN.toShort()
                }
                val interruptible = object : FilterInputStream(stream) {
                    override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                        do {
                            cancellation.throwIfCanceled()
                            RenderPolicy.checkCancellation()
                            val available = Os.poll(arrayOf(poll), 100)
                        } while (available == 0)
                        cancellation.throwIfCanceled()
                        return super.read(bytes, offset, length)
                    }
                }
                read(interruptible)
            }
        } ?: throw DocumentException(ReadFailure.ACCESS)
    }

    // Optional provider methods are not universally implemented. Do not swallow
    // SecurityException, OperationCanceledException, or failures reading the actual stream.
    private fun <T> optionalMetadata(block: () -> T): T? = try {
        block()
    } catch (_: UnsupportedOperationException) { null }
      catch (_: IllegalArgumentException) { null }

    fun retainReadAccess(uri: Uri, flags: Int) {
        if (flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0 &&
            flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0) {
            try {
                resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {
                // Some providers offer only temporary access. Opening still works for this session.
            }
        }
    }

    fun recentFiles(): List<RecentFile> = try {
        val array = JSONArray(preferences.getString("entries", "[]"))
        (0 until array.length()).map { index ->
            val entry = array.getJSONObject(index)
            RecentFile(entry.getString("uri"), entry.getString("name"), entry.getLong("openedAt"), ReadingState(
                DisplayMode.entries.find { it.name == entry.optString("mode") },
                entry.optInt("scroll", 0).coerceAtLeast(0),
                FileKind.entries.find { it.name == entry.optString("format") },
                TextEncoding.entries.find { it.name == entry.optString("encoding") }), entry.optBoolean("pinned"))
        }
    } catch (_: org.json.JSONException) { emptyList() }

    fun remember(uri: Uri, name: String) {
        val previous = recentFiles()
        val old = previous.find { it.uri == uri.toString() }
        val updated = bounded(listOf(RecentFile(uri.toString(), name, System.currentTimeMillis(), old?.reading ?: ReadingState(), old?.pinned ?: false)) +
            previous.filterNot { it.uri == uri.toString() })
        save(updated)
        previous.filter { old -> updated.none { it.uri == old.uri } }.forEach { release(it.uri) }
    }

    fun readingState(uri: Uri): ReadingState = recentFiles().find { it.uri == uri.toString() }?.reading ?: ReadingState()

    fun saveReadingState(uri: Uri, state: ReadingState) {
        // Updating state must never resurrect an entry the user removed or change recency.
        save(recentFiles().map { if (it.uri == uri.toString()) it.copy(reading = state) else it })
    }

    fun remove(uri: String) {
        save(recentFiles().filterNot { it.uri == uri })
        release(uri)
    }

    fun clear() {
        commitRemoval()
        val previous = recentFiles()
        save(emptyList())
        previous.forEach { release(it.uri) }
    }

    private fun save(files: List<RecentFile>) {
        val array = JSONArray()
        files.forEach { file ->
            array.put(JSONObject().put("uri", file.uri).put("name", file.name).put("openedAt", file.openedAt).apply {
                if (file.pinned) put("pinned", true)
                if (file.reading != ReadingState()) {
                    put("mode", file.reading.mode?.name)
                    put("scroll", file.reading.scroll)
                    put("format", file.reading.format?.name)
                    put("encoding", file.reading.encoding?.name)
                }
            })
        }
        preferences.edit().putString("entries", array.toString()).apply()
    }

    private fun bounded(files: List<RecentFile>): List<RecentFile> =
        files.filter { it.pinned }.take(MAX_PINS) + files.filterNot { it.pinned }.take(30 - files.count { it.pinned }.coerceAtMost(MAX_PINS))

    fun togglePin(uri: String): Boolean {
        val files = recentFiles()
        val file = files.find { it.uri == uri } ?: return false
        if (!file.pinned && files.count { it.pinned } >= MAX_PINS) return false
        save(files.map { if (it.uri == uri) it.copy(pinned = !it.pinned) else it })
        return true
    }

    fun removeWithUndo(uri: String, now: Long = System.currentTimeMillis()) {
        commitRemoval()
        val file = recentFiles().find { it.uri == uri } ?: return
        // Keep the original metadata and its grant until Undo expires or is committed.
        val entry = JSONArray(preferences.getString("entries", "[]"))
        val value = (0 until entry.length()).map { entry.getJSONObject(it) }.first { it.getString("uri") == file.uri }
        preferences.edit().putString("removed", value.toString()).putLong("undoUntil", now + UNDO_MS).apply()
        save(recentFiles().filterNot { it.uri == uri })
    }

    fun undoRemainingMillis() = (preferences.getLong("undoUntil", 0) - System.currentTimeMillis()).coerceAtLeast(0)

    fun undoAvailable(now: Long = System.currentTimeMillis()): Boolean {
        if (now >= preferences.getLong("undoUntil", 0)) commitRemoval()
        return preferences.contains("removed")
    }

    fun undoRemoval(): Boolean {
        if (!undoAvailable()) return false
        val old = preferences.getString("entries", "[]")!!
        val removed = preferences.getString("removed", null) ?: return false
        val restored = JSONObject(removed)
        if (recentFiles().count { it.pinned } >= MAX_PINS) restored.remove("pinned")
        val array = JSONArray(old).put(restored)
        preferences.edit().putString("entries", array.toString()).remove("removed").remove("undoUntil").apply()
        val files = recentFiles().distinctBy { it.uri }.sortedByDescending { it.openedAt }
        val restoredFile = files.first { it.uri == restored.getString("uri") }
        val limited = bounded(listOf(restoredFile) + files.filterNot { it.uri == restoredFile.uri })
        save(limited)
        files.filter { file -> limited.none { it.uri == file.uri } }.forEach { release(it.uri) }
        return true
    }

    fun commitRemoval() {
        val value = preferences.getString("removed", null)
        preferences.edit().remove("removed").remove("undoUntil").apply()
        if (value != null) try { release(JSONObject(value).getString("uri")) } catch (_: org.json.JSONException) { }
    }

    fun sessionReferences(): Set<String> = preferences.getStringSet("sessions", emptySet()).orEmpty().toSet()

    fun setSessionReferences(references: Set<String>) {
        val old = sessionReferences()
        preferences.edit().putStringSet("sessions", references).apply()
        (old - references).forEach(::release)
    }

    private fun release(value: String) {
        if (recentFiles().any { it.uri == value } || value in sessionReferences()) return
        val removed = preferences.getString("removed", null)
        if (removed != null && runCatching { JSONObject(removed).getString("uri") }.getOrNull() == value) return
        try {
            val uri = Uri.parse(value)
            if (resolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }) {
                resolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (_: SecurityException) { /* The provider may already have revoked the grant. */ }
    }
    companion object { const val MAX_PINS = 10; const val UNDO_MS = 10_000L }
}
