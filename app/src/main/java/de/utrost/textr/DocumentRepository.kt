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

data class RecentFile(val uri: String, val name: String, val openedAt: Long)

class DocumentRepository(private val context: Context) {
    private val resolver = context.contentResolver
    private val preferences = context.getSharedPreferences("recent_files", Context.MODE_PRIVATE)

    fun read(uri: Uri, mime: String?, cancellation: CancellationSignal): TextDocument {
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
                TextDocuments.read(interruptible, name, type)
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
            RecentFile(entry.getString("uri"), entry.getString("name"), entry.getLong("openedAt"))
        }
    } catch (_: org.json.JSONException) { emptyList() }

    fun remember(uri: Uri, name: String) {
        val previous = recentFiles()
        val updated = (listOf(RecentFile(uri.toString(), name, System.currentTimeMillis())) +
            previous.filterNot { it.uri == uri.toString() }).take(30)
        save(updated)
        previous.filter { old -> updated.none { it.uri == old.uri } }.forEach { release(it.uri) }
    }

    fun remove(uri: String) {
        save(recentFiles().filterNot { it.uri == uri })
        release(uri)
    }

    fun clear() {
        val previous = recentFiles()
        save(emptyList())
        previous.forEach { release(it.uri) }
    }

    private fun save(files: List<RecentFile>) {
        val array = JSONArray()
        files.forEach { file ->
            array.put(JSONObject().put("uri", file.uri).put("name", file.name).put("openedAt", file.openedAt))
        }
        preferences.edit().putString("entries", array.toString()).apply()
    }

    private fun release(value: String) {
        try {
            val uri = Uri.parse(value)
            if (resolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }) {
                resolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (_: SecurityException) { /* The provider may already have revoked the grant. */ }
    }
}
