package de.utrost.textr

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class CollectionMember(val uri: String, val name: String)
data class ReadingCollection(val id: String, val name: String, val members: List<CollectionMember>)

/** Metadata only. Collections independently retain existing read grants, never source contents. */
class ReadingCollections(private val context: Context) {
    private val preferences = context.getSharedPreferences("reading_collections", Context.MODE_PRIVATE)
    companion object { const val MAX_COLLECTIONS = 20; const val MAX_MEMBERS = 30; const val MAX_TOTAL = 200; const val MAX_NAME = 80 }
    fun entries(): List<ReadingCollection> = try {
        val array = JSONArray(preferences.getString("entries", "[]"))
        var total = 0
        (0 until minOf(array.length(), MAX_COLLECTIONS)).map { index ->
            val value = array.getJSONObject(index)
            val members = value.getJSONArray("members")
            val read = (0 until minOf(members.length(), MAX_MEMBERS, MAX_TOTAL - total)).mapNotNull { at ->
                val member = members.getJSONObject(at)
                val uri = member.getString("uri")
                if (validUri(uri)) CollectionMember(uri, member.getString("name").take(256)) else null
            }.distinctBy { it.uri }
            total += read.size
            ReadingCollection(value.getString("id").take(64), value.getString("name").take(MAX_NAME), read)
        }
    } catch (_: org.json.JSONException) { emptyList() }
    private fun validUri(value: String) = value.length <= 4096 && Uri.parse(value).scheme in setOf("content", "file")
    private fun validName(name: String, oldId: String? = null) = name.trim().length in 1..MAX_NAME &&
        name.none { it.isISOControl() } && entries().none { it.id != oldId && it.name.equals(name.trim(), ignoreCase = true) }
    fun create(name: String, member: CollectionMember? = null): String? {
        val old = entries()
        if (old.size >= MAX_COLLECTIONS || !validName(name)) return null
        if (member != null && (!validUri(member.uri) || old.sumOf { it.members.size } >= MAX_TOTAL)) return null
        val id = UUID.randomUUID().toString()
        val members = listOfNotNull(member?.copy(name = member.name.take(256)))
        return if (save(old + ReadingCollection(id, name.trim(), members))) id else null
    }
    fun rename(id: String, name: String): Boolean {
        if (!validName(name, id) || entries().none { it.id == id }) return false
        return save(entries().map { if (it.id == id) it.copy(name = name.trim()) else it })
    }
    fun add(id: String, member: CollectionMember): Boolean {
        val old = entries(); val collection = old.find { it.id == id } ?: return false
        if (!validUri(member.uri) || collection.members.any { it.uri == member.uri } || collection.members.size >= MAX_MEMBERS || old.sumOf { it.members.size } >= MAX_TOTAL) return false
        return save(old.map { if (it.id == id) it.copy(members = it.members + member.copy(name = member.name.take(256))) else it })
    }
    fun remove(id: String, uri: String) = save(entries().map { if (it.id == id) it.copy(members = it.members.filterNot { member -> member.uri == uri }) else it })
    fun delete(id: String) = save(entries().filterNot { it.id == id })
    fun clear() = save(emptyList())
    fun reconnect(id: String, oldUri: String, member: CollectionMember): Boolean {
        val old = entries(); val collection = old.find { it.id == id } ?: return false
        if (!validUri(member.uri) || collection.members.none { it.uri == oldUri } || collection.members.any { it.uri == member.uri && it.uri != oldUri }) return false
        return save(old.map { if (it.id == id) it.copy(members = it.members.map { item -> if (item.uri == oldUri) member.copy(name = member.name.take(256)) else item }) else it })
    }
    fun reconcile() { DocumentRepository(context).setCollectionReferences(entries().flatMap { it.members }.map { it.uri }.toSet()) }
    private fun save(values: List<ReadingCollection>): Boolean {
        val array = JSONArray()
        values.forEach { value ->
            val members = JSONArray()
            value.members.forEach { members.put(JSONObject().put("uri", it.uri).put("name", it.name)) }
            array.put(JSONObject().put("id", value.id).put("name", value.name).put("members", members))
        }
        if (!preferences.edit().putString("entries", array.toString()).commit()) return false
        DocumentRepository(context).setCollectionReferences(values.flatMap { it.members }.map { it.uri }.toSet())
        return true
    }
}
