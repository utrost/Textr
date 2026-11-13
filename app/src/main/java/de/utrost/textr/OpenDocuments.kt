package de.utrost.textr

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Inactive URI documents retain metadata only. Search queries and shared text stay in memory. */
data class OpenDocument(
    val key: String,
    val uri: String?,
    val name: String,
    var reading: ReadingState = ReadingState(),
    var query: String = "",
    var searching: Boolean = false,
    val shared: TextDocument? = null,
    var sourceSearch: SourceSearchOptions = SourceSearchOptions()
)

class OpenDocuments(context: Context) {
    private val preferences = context.getSharedPreferences("open_documents", Context.MODE_PRIVATE)
    private val repository = DocumentRepository(context)
    val entries = mutableListOf<OpenDocument>()
    init {
        try {
            val array = JSONArray(preferences.getString("entries", "[]"))
            for (index in 0 until minOf(array.length(), LIMIT)) {
                val entry = array.getJSONObject(index)
                val uri = entry.getString("uri")
                if (entries.none { it.uri == uri }) entries.add(OpenDocument(uri, uri, entry.getString("name"), ReadingState(
                    DisplayMode.entries.find { it.name == entry.optString("mode") }, entry.optInt("scroll").coerceAtLeast(0),
                    FileKind.entries.find { it.name == entry.optString("format") },
                    TextEncoding.entries.find { it.name == entry.optString("encoding") })))
            }
        } catch (_: org.json.JSONException) { entries.clear() }
        save()
    }
    fun opened(uri: String?, original: TextDocument): OpenDocument {
        val key = uri ?: "shared-text"
        val previous = entries.find { it.key == key }
        entries.removeAll { it.key == key }
        val entry = if (uri != null) previous?.copy(name = original.name) ?: OpenDocument(key, uri, original.name)
            else OpenDocument(key, null, original.name, shared = original)
        entries.add(entry)
        while (entries.size > LIMIT) entries.removeAt(0)
        save()
        return entry
    }
    fun close(key: String) { entries.removeAll { it.key == key }; save() }
    fun save() {
        val array = JSONArray()
        entries.filter { it.uri != null }.forEach {
            array.put(JSONObject().put("uri", it.uri).put("name", it.name).put("mode", it.reading.mode?.name)
                .put("scroll", it.reading.scroll).put("format", it.reading.format?.name).put("encoding", it.reading.encoding?.name))
        }
        preferences.edit().putString("entries", array.toString()).apply()
        repository.setSessionReferences(entries.mapNotNull { it.uri }.toSet())
    }
    companion object { const val LIMIT = 5 }
}
