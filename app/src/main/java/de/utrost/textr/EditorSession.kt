package de.utrost.textr

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import java.util.UUID
import java.util.concurrent.Executors

data class EditorSnapshot(val document: TextDocument, val uri: String?, val bom: Boolean)
object EditorInbox {
    private var pending: Pair<String, EditorSnapshot>? = null
    @Synchronized fun put(value: EditorSnapshot): String? {
        if (!EditBuffer.accepts(value.document.text) || value.document.kind !in setOf(FileKind.TEXT, FileKind.MARKDOWN)) return null
        return UUID.randomUUID().toString().also { pending = it to value }
    }
    @Synchronized fun take(token: String?): EditorSnapshot? = pending?.takeIf { it.first == token }?.also { pending = null }?.second
}

data class EditorFindState(var query: String = "", var replacement: String = "", var options: LiteralOptions = LiteralOptions(),
    var selected: Int = -1, var open: Boolean = false)

class EditorSession(application: Application) : AndroidViewModel(application) {
    private val store = EditDraftStore(application)
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val checkpointLock = Any()
    private var pending: EditDraft? = null
    private var draining = false
    private var generation = 0
    val findState = EditorFindState()
    var initialized = false
    var loading = false; private set
    var draft: EditDraft? = null; private set
    var buffer: EditBuffer? = null; private set
    var blockedByExisting = false; private set
    var unavailable = false; private set
    var recoveryFailed = false; private set
    var checkpointRevision = -1L; private set
    var listener: (() -> Unit)? = null
    var handledOutput: String? = null
    val dirty get() = buffer?.dirty == true || draft?.let { it.encoding != it.baselineEncoding || it.bom != it.baselineBom } == true
    private fun notifyChange() { listener?.invoke() }
    fun initialize(snapshot: EditorSnapshot?) {
        val request = ++generation
        loading = true; unavailable = false; notifyChange()
        worker.execute {
            try {
                val old = store.read()
                val blocked = old != null && snapshot != null
                val value = old ?: snapshot?.let { EditDraft.new(it.document, it.uri, it.bom).also { check(store.create(it)) } }
                handler.post {
                    if (request != generation) return@post
                    loading = false; recoveryFailed = false; blockedByExisting = blocked
                    draft = value
                    if (value != null && !blocked) restore(value) else unavailable = value == null
                    notifyChange()
                }
            } catch (_: Exception) {
                handler.post { if (request == generation) { loading = false; unavailable = true; recoveryFailed = true; notifyChange() } }
            }
        }
    }
    fun retryRecovery() {
        if (loading) return
        if (buffer == null) { initialize(null); return }
        draft?.let { queueCheckpoint(it) }
    }
    fun resumeExisting() { draft?.let { blockedByExisting = false; restore(it); notifyChange() } }
    private fun restore(value: EditDraft) {
        buffer = EditBuffer(value.text, value.baseline).also { it.restoreRevision(value.revision); it.select(EditSelection(value.selectionStart, value.selectionEnd)) }
        checkpointRevision = value.revision
    }
    fun changed() {
        val buffer = buffer ?: return
        val value = draft ?: return
        if (value.revision == buffer.revision && value.text == buffer.text) { notifyChange(); return }
        val snapshot = value.copy(text = buffer.text, revision = buffer.revision, baseline = buffer.baselineFingerprint(), updatedAt = System.currentTimeMillis(),
            selectionStart = buffer.selection.start, selectionEnd = buffer.selection.end)
        draft = snapshot
        queueCheckpoint(snapshot); notifyChange()
    }
    fun saveSelection(selection: EditSelection) {
        val buffer = buffer ?: return
        buffer.select(selection)
        if (checkpointRevision < 0 && !dirty) return
        val value = draft ?: return
        val snapshot = value.copy(selectionStart = buffer.selection.start, selectionEnd = buffer.selection.end)
        draft = snapshot; queueCheckpoint(snapshot)
    }
    private fun queueCheckpoint(snapshot: EditDraft) {
        synchronized(checkpointLock) {
            pending = snapshot
            if (draining) return
            draining = true
        }
        worker.execute {
            while (true) {
                val value = synchronized(checkpointLock) {
                    val value = pending; pending = null
                    if (value == null) draining = false
                    value
                } ?: break
                val saved = try { store.checkpoint(value) } catch (_: Exception) { false }
                handler.post {
                    if (draft?.id == value.id) {
                        if (saved) checkpointRevision = maxOf(checkpointRevision, value.revision)
                        recoveryFailed = !saved; notifyChange()
                    }
                }
            }
        }
    }
    fun format(encoding: TextEncoding, bom: Boolean) {
        val value = draft ?: return
        if (value.encoding == encoding && value.bom == bom) return
        // Formatting is explicit output metadata; text history stays independent.
        draft = value.copy(encoding = encoding, bom = bom, updatedAt = System.currentTimeMillis())
        buffer?.touch(); changed()
    }
    fun saved(status: OutputStatus, completed: (Boolean) -> Unit) {
        val value = draft ?: return completed(false)
        val buffer = buffer ?: return completed(false)
        if (status.result != OutputResult.SAVED || status.draftId != value.id || status.revision != buffer.revision) return completed(false)
        synchronized(checkpointLock) { pending = null }
        loading = true; notifyChange()
        worker.execute {
            val deleted = try { store.delete(value.id, status.revision) } catch (_: Exception) { false }
            handler.post {
                loading = false
                if (deleted && draft?.id == value.id && buffer.revision == status.revision) {
                    buffer.markSaved(); draft = value.copy(baseline = buffer.baselineFingerprint(), baselineEncoding = value.encoding, baselineBom = value.bom); checkpointRevision = -1
                } else recoveryFailed = true
                handledOutput = "${status.draftId}:${status.revision}"
                notifyChange(); completed(deleted)
            }
        }
    }
    fun discard(completed: (Boolean) -> Unit) {
        val value = draft
        if (value == null) {
            if (!unavailable || !recoveryFailed) return completed(true)
            loading = true; notifyChange()
            worker.execute {
                val removed = try { store.discardUnreadable() } catch (_: Exception) { false }
                handler.post { loading = false; notifyChange(); completed(removed) }
            }
            return
        }
        synchronized(checkpointLock) { pending = null }
        loading = true; notifyChange()
        worker.execute {
            val deleted = try { store.delete(value.id) } catch (_: Exception) { false }
            handler.post {
                loading = false
                if (deleted) { draft = null; buffer = null } else recoveryFailed = true
                notifyChange(); completed(deleted)
            }
        }
    }
    override fun onCleared() {
        listener = null; generation++
        // Finish already queued checkpoints even when the activity is closed.
        worker.shutdown(); super.onCleared()
    }
}
