package de.utrost.textr

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Bookmark(val id: String, val uri: String, val fileName: String, val label: String,
    val fraction: Double, val fingerprint: String, val reading: ReadingState)

/** Independent owners of URI permissions. No source contents or excerpts are retained. */
class Bookmarks(private val context: Context) {
    private val preferences = context.getSharedPreferences("bookmarks", Context.MODE_PRIVATE)
    fun entries(): List<Bookmark> = try {
        val array = JSONArray(preferences.getString("entries", "[]"))
        (0 until minOf(array.length(), LIMIT)).map { index ->
            val value = array.getJSONObject(index)
            Bookmark(value.getString("id"), value.getString("uri"), value.getString("fileName"),
                value.getString("label").take(LABEL_LIMIT), value.optDouble("fraction", 0.0).takeIf { it.isFinite() }?.coerceIn(0.0, 1.0) ?: 0.0,
                value.getString("fingerprint"), ReadingState(
                    DisplayMode.entries.find { it.name == value.optString("mode") }, 0,
                    FileKind.entries.find { it.name == value.optString("format") },
                    TextEncoding.entries.find { it.name == value.optString("encoding") }, csvOptions(value)))
        }
    } catch (_: org.json.JSONException) { emptyList() }

    fun add(uri: String, name: String, label: String, fraction: Double, fingerprint: String, reading: ReadingState): Boolean {
        val old = entries()
        if (old.size >= LIMIT || label.isBlank()) return false
        save(old + Bookmark(UUID.randomUUID().toString(), uri, name, label.trim().take(LABEL_LIMIT),
            fraction.takeIf { it.isFinite() }?.coerceIn(0.0, 1.0) ?: 0.0, fingerprint, reading))
        return true
    }
    fun remove(id: String) = save(entries().filterNot { it.id == id })
    fun clear() = save(emptyList())
    fun rename(id: String, label: String) {
        if (label.isNotBlank()) save(entries().map { if (it.id == id) it.copy(label = label.trim().take(LABEL_LIMIT)) else it })
    }
    fun reconnect(id: String, uri: String, name: String) = save(entries().map {
        if (it.id == id) it.copy(uri = uri, fileName = name) else it
    })
    private fun save(entries: List<Bookmark>) {
        val array = JSONArray()
        entries.forEach { bookmark ->
            array.put(JSONObject().put("id", bookmark.id).put("uri", bookmark.uri).put("fileName", bookmark.fileName)
                .put("label", bookmark.label).put("fraction", bookmark.fraction).put("fingerprint", bookmark.fingerprint)
                .put("mode", bookmark.reading.mode?.name).put("format", bookmark.reading.format?.name)
                .put("encoding", bookmark.reading.encoding?.name).put("delimiter", bookmark.reading.csv.delimiter.toString())
                .put("csvHeader", bookmark.reading.csv.firstRowHeader))
        }
        preferences.edit().putString("entries", array.toString()).apply()
        DocumentRepository(context).setBookmarkReferences(entries.map { it.uri }.toSet())
    }
    companion object { const val LIMIT = 100; const val LABEL_LIMIT = 80 }
}
