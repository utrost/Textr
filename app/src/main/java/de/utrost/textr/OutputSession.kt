package de.utrost.textr

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import androidx.lifecycle.AndroidViewModel
import java.io.OutputStream
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** A single explicit output snapshot. Content stays in this retained model, never in history/Bundle. */
data class OutputRequest(val name: String, val mime: String, val text: String, val sourceUri: String?,
    val encoding: TextEncoding = TextEncoding.UTF8, val bom: Boolean = false,
    val draftId: String? = null, val revision: Long = 0)
enum class OutputPhase { IDLE, PICKING, WRITING, COMPLETE }
enum class OutputResult { SAVED, CANCELLED, FAILED, REFUSED, INTERRUPTED }
data class OutputStatus(val phase: OutputPhase = OutputPhase.IDLE, val name: String = "",
    val target: Uri? = null, val result: OutputResult? = null, val partial: Boolean = false,
    val draftId: String? = null, val revision: Long = 0)

class OutputSession(application: Application) : AndroidViewModel(application) {
    private val resolver = application.contentResolver
    private val metadata = application.getSharedPreferences("output_operation", 0)
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val cleanup = ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue(1))
    private val removing = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private var request: OutputRequest? = null
    private var signal: CancellationSignal? = null
    @Volatile private var writingThread: Thread? = null
    @Volatile var status = OutputStatus(); private set
    var listener: ((OutputStatus) -> Unit)? = null
    val busy get() = status.phase != OutputPhase.IDLE
    init {
        metadata.getString("target", null)?.let { target ->
            val saved = OutputResult.entries.find { it.name == metadata.getString("result", null) } ?: OutputResult.INTERRUPTED
            status = OutputStatus(OutputPhase.COMPLETE, metadata.getString("name", "").orEmpty(), Uri.parse(target), saved,
                metadata.getBoolean("partial", saved != OutputResult.SAVED), metadata.getString("draftId", null), metadata.getLong("revision", 0))
        }
    }
    private fun persist(value: OutputStatus) {
        metadata.edit().clear().putString("name", value.name).putString("target", value.target?.toString())
            .putString("result", value.result?.name).putBoolean("partial", value.partial)
            .putString("draftId", value.draftId).putLong("revision", value.revision).commit()
    }

    fun begin(value: OutputRequest): Intent {
        check(!busy)
        require(value.text.length <= MAX_FILE_BYTES && value.mime.startsWith("text/"))
        request = value
        publish(OutputStatus(OutputPhase.PICKING, value.name, draftId = value.draftId, revision = value.revision))
        return Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
            .setType(value.mime).putExtra(Intent.EXTRA_TITLE, value.name)
    }
    /** Used after process death: no source contents are serialized or silently reconstructed. */
    fun restorePending(name: String, sourceUri: String?) {
        if (status.phase != OutputPhase.IDLE) return
        request = OutputRequest(name, "text/plain", "", sourceUri)
        publish(OutputStatus(OutputPhase.PICKING, name, result = OutputResult.INTERRUPTED))
    }
    fun pickerResult(target: Uri?) {
        if (status.phase != OutputPhase.PICKING) return
        val snapshot = request ?: return
        if (target == null) {
            request = null
            publish(status.copy(phase = OutputPhase.IDLE))
            return
        }
        val interrupted = status.result == OutputResult.INTERRUPTED
        val cancellation = CancellationSignal().also { signal = it }
        publish(status.copy(phase = OutputPhase.WRITING, target = target))
        worker.execute {
            writingThread = Thread.currentThread()
            persist(OutputStatus(OutputPhase.WRITING, snapshot.name, target, partial = true, draftId = snapshot.draftId, revision = snapshot.revision))
            var mayDelete = false
            var result = OutputResult.FAILED
            var partial = false
            try {
                if (!safeTarget(target, snapshot.sourceUri)) {
                    result = OutputResult.REFUSED
                } else {
                    mayDelete = true
                    if (interrupted) result = OutputResult.INTERRUPTED
                    else {
                        write(target, snapshot, cancellation)
                        result = OutputResult.SAVED
                    }
                }
            } catch (_: Exception) {
                result = if (cancellation.isCanceled || Thread.currentThread().isInterrupted) OutputResult.CANCELLED else OutputResult.FAILED
            } finally {
                writingThread = null
                Thread.interrupted()
                if (mayDelete && result != OutputResult.SAVED) partial = !removeOutput(target)
                else if (result == OutputResult.FAILED) partial = true
                val completed = OutputStatus(OutputPhase.COMPLETE, snapshot.name, target, result, partial, snapshot.draftId, snapshot.revision)
                persist(completed)
                handler.post { request = null; signal = null; publish(completed) }
            }
        }
    }
    fun cancel() { signal?.cancel(); writingThread?.interrupt() }
    fun acknowledge() { if (status.phase == OutputPhase.COMPLETE) { metadata.edit().clear().apply(); publish(OutputStatus()) } }
    fun pickerUnavailable() { if (status.phase == OutputPhase.PICKING) { request = null; publish(status.copy(phase = OutputPhase.COMPLETE, result = OutputResult.FAILED)) } }
    fun pendingSource() = request?.sourceUri

    private fun publish(value: OutputStatus) { status = value; listener?.invoke(value) }
    private fun documentKey(uri: Uri): String =
        if (DocumentsContract.isDocumentUri(getApplication(), uri))
            "${uri.authority?.substringAfterLast('@')}/document/${DocumentsContract.getDocumentId(uri)}"
        else uri.normalizeScheme().toString()
    private fun safeTarget(target: Uri, source: String?): Boolean {
        if (target.scheme != "content" || target.authority.isNullOrBlank()) return false
        val key = documentKey(target)
        // Timed-out provider deletion may still finish remotely. Do not reuse its URI meanwhile.
        if (key in removing) return false
        val original = source?.let(Uri::parse) ?: return true
        return target.normalizeScheme() != original.normalizeScheme() && key != documentKey(original)
    }
    private fun write(target: Uri, snapshot: OutputRequest, cancellation: CancellationSignal) {
        fun check() { cancellation.throwIfCanceled(); RenderPolicy.checkCancellation() }
        check()
        val descriptor = resolver.openFileDescriptor(target, "w", cancellation) ?: throw java.io.IOException("No output descriptor")
        try {
            ParcelFileDescriptor.AutoCloseOutputStream(descriptor).use { stream ->
                try {
                val poll = StructPollfd().apply { fd = descriptor.fileDescriptor; events = OsConstants.POLLOUT.toShort() }
                val interruptible = object : OutputStream() {
                    override fun write(value: Int) = write(byteArrayOf(value.toByte()), 0, 1)
                    override fun write(bytes: ByteArray, offset: Int, length: Int) {
                        var at = offset
                        while (at < offset + length) {
                            check()
                            val ready = Os.poll(arrayOf(poll), 100)
                            if (ready == 0) continue
                            if (poll.revents.toInt() and (OsConstants.POLLERR or OsConstants.POLLHUP or OsConstants.POLLNVAL) != 0) throw java.io.IOException("Output unavailable")
                            val count = minOf(4096, offset + length - at)
                            stream.write(bytes, at, count); at += count
                        }
                    }
                }
                EncodedOutput.write(snapshot.text, snapshot.encoding, snapshot.bom, interruptible, ::check)
                check(); stream.flush(); descriptor.checkError(); check()
                } catch (error: Exception) {
                    try { descriptor.closeWithError("Output interrupted") } catch (_: Exception) { }
                    throw error
                }
            }
        } catch (error: Exception) {
            try { descriptor.closeWithError("Output interrupted") } catch (_: Exception) { }
            throw error
        }
        check()
    }
    private fun removeOutput(target: Uri): Boolean {
        val key = documentKey(target)
        val guard = Any()
        var started = false
        var cancelled = false
        var task: java.util.concurrent.Future<Boolean>? = null
        removing.add(key)
        return try {
            task = cleanup.submit<Boolean> {
                synchronized(guard) { if (cancelled) return@submit false; started = true }
                try {
                    DocumentsContract.isDocumentUri(getApplication(), target) && DocumentsContract.deleteDocument(resolver, target)
                } finally { removing.remove(key) }
            }
            task.get(2, TimeUnit.SECONDS)
        } catch (_: Exception) {
            synchronized(guard) { cancelled = true; if (!started) removing.remove(key) }
            task?.cancel(true)
            false
        }
    }
    override fun onCleared() {
        listener = null; cancel(); worker.shutdown(); cleanup.shutdown()
        super.onCleared()
    }
}
