package de.utrost.textr

import android.content.Context
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class EditDraft(val id: String, val name: String, val sourceUri: String?, val text: String,
    val encoding: TextEncoding, val bom: Boolean, val endings: String, val baseline: String,
    val revision: Long = 0, val updatedAt: Long = System.currentTimeMillis(),
    val baselineEncoding: TextEncoding = encoding, val baselineBom: Boolean = bom,
    val selectionStart: Int = 0, val selectionEnd: Int = selectionStart,
    val markdown: Boolean = TextDocuments.kind(name, null) == FileKind.MARKDOWN) {
    companion object {
        fun new(document: TextDocument, sourceUri: String?, bom: Boolean = false) = EditDraft(
            UUID.randomUUID().toString(), document.name, sourceUri, document.text,
            TextEncoding.entries.firstOrNull { it.charsetName.equals(document.encoding, true) } ?: TextEncoding.UTF8,
            bom, document.info.endings, textFingerprint(document.text), markdown = document.kind == FileKind.MARKDOWN)
    }
}

/** The sole content-bearing local recovery file. No automatic expiry or history coupling. */
class EditDraftStore(context: Context) {
    private val directory = File(context.noBackupFilesDir, "edits")
    private val file = AtomicFile(File(directory, "current.json"))
    companion object { private val lock = Any(); const val MAX_STORED_BYTES = 2 * 1024 * 1024 }
    fun exists(): Boolean = file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()
    fun read(): EditDraft? = synchronized(lock) { readLocked() }
    private fun readLocked(): EditDraft? {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return null
        val bytes = file.openRead().use { input ->
            val bytes = input.readBytesBounded(MAX_STORED_BYTES)
            bytes
        }
        val json = JSONObject(Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString())
        require(json.getInt("version") == 1)
        val id = json.getString("id"); require(id.length in 1..64)
        val text = json.getString("text"); require(EditBuffer.accepts(text))
        val baseline = json.getString("baseline"); require(baseline.matches(Regex("[0-9a-f]{64}")))
        return EditDraft(id, json.getString("name").take(256), json.optString("sourceUri").takeIf { it.isNotEmpty() }, text,
            TextEncoding.valueOf(json.getString("encoding")), json.getBoolean("bom"), json.getString("endings").take(32), baseline,
            json.getLong("revision").coerceAtLeast(0), json.getLong("updatedAt"),
            TextEncoding.valueOf(json.optString("baselineEncoding", json.getString("encoding"))), json.optBoolean("baselineBom", json.getBoolean("bom")),
            json.optInt("selectionStart").coerceIn(0, text.length), json.optInt("selectionEnd").coerceIn(0, text.length),
            json.optBoolean("markdown", TextDocuments.kind(json.getString("name"), null) == FileKind.MARKDOWN))
    }
    fun create(draft: EditDraft): Boolean = synchronized(lock) {
        if (readLocked() != null) return false
        writeLocked(draft); true
    }
    fun checkpoint(draft: EditDraft): Boolean = synchronized(lock) {
        val old = readLocked()
        if (old != null && (old.id != draft.id || old.revision > draft.revision)) return false
        writeLocked(draft); true
    }
    fun delete(id: String, revision: Long? = null): Boolean = synchronized(lock) {
        val old = readLocked() ?: return true
        if (old.id != id || revision != null && old.revision != revision) return false
        file.delete(); !file.baseFile.exists()
    }
    fun discardUnreadable(): Boolean = synchronized(lock) {
        try { if (readLocked() != null) return false } catch (_: Exception) { }
        file.delete(); !exists()
    }
    private fun writeLocked(draft: EditDraft) {
        require(EditBuffer.accepts(draft.text))
        val json = JSONObject().put("version", 1).put("id", draft.id).put("name", draft.name.take(256))
            .put("sourceUri", draft.sourceUri.orEmpty()).put("text", draft.text).put("encoding", draft.encoding.name)
            .put("bom", draft.bom).put("endings", draft.endings).put("baseline", draft.baseline)
            .put("revision", draft.revision).put("updatedAt", draft.updatedAt)
            .put("baselineEncoding", draft.baselineEncoding.name).put("baselineBom", draft.baselineBom)
            .put("selectionStart", draft.selectionStart).put("selectionEnd", draft.selectionEnd).put("markdown", draft.markdown)
        val encoded = Charsets.UTF_8.newEncoder().encode(java.nio.CharBuffer.wrap(json.toString()))
        val bytes = ByteArray(encoded.remaining()).also { encoded.get(it) }; require(bytes.size <= MAX_STORED_BYTES)
        check(directory.isDirectory || directory.mkdirs())
        val stream = file.startWrite()
        try { stream.write(bytes); file.finishWrite(stream) }
        catch (error: Exception) { file.failWrite(stream); throw error }
    }
}

private fun java.io.InputStream.readBytesBounded(maximum: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
    while (true) {
        val count = read(buffer, 0, minOf(buffer.size, maximum + 1 - output.size()))
        if (count < 0) break
        if (count == 0) continue
        output.write(buffer, 0, count); require(output.size() <= maximum)
    }
    return output.toByteArray()
}
