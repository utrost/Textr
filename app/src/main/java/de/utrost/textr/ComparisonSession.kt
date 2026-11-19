package de.utrost.textr

import android.app.Application
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.Future

internal fun sourceFingerprint(text: String): String = java.security.MessageDigest.getInstance("SHA-256")
    .digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
data class ComparisonRef(val uri: String?, val name: String, val encoding: TextEncoding? = null, val fingerprint: String? = null)
data class ComparisonSnapshot(val reference: ComparisonRef, val document: TextDocument, val capturedAt: Long = System.currentTimeMillis())

/** One bounded handoff. An intent/Bundle never contains source contents. */
object ComparisonInbox {
    private var pending: Pair<String, ComparisonSnapshot>? = null
    @Synchronized fun put(snapshot: ComparisonSnapshot): String? {
        if (snapshot.document.bytes > LineDiff.MAX_BYTES || snapshot.document.text.length > LineDiff.MAX_CHARS) { pending = null; return null }
        val token = UUID.randomUUID().toString(); pending = token to snapshot; return token
    }
    @Synchronized fun take(token: String?): ComparisonSnapshot? {
        val value = pending?.takeIf { it.first == token } ?: return null
        pending = null; return value.second
    }
}
enum class ComparisonFailure { READ, INPUT_LIMIT, CANCELLED, LOST_SHARED }

class ComparisonSession(application: Application) : AndroidViewModel(application) {
    private val repository = DocumentRepository(application)
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var work: Future<*>? = null
    private var signal: CancellationSignal? = null
    private var generation = 0
    var initialized = false
    var left: ComparisonSnapshot? = null; private set
    var right: ComparisonSnapshot? = null; private set
    var leftRef: ComparisonRef? = null; private set
    var rightRef: ComparisonRef? = null; private set
    var result: LineDiffResult? = null; private set
    var busy = false; private set
    var failure: ComparisonFailure? = null; private set
    var changedSources = false; private set
    var selectedChange = 0
    var listener: (() -> Unit)? = null

    fun initial(snapshot: ComparisonSnapshot) { left = snapshot; leftRef = snapshot.reference; listener?.invoke() }
    fun load(isLeft: Boolean, reference: ComparisonRef) {
        stopWork()
        if (isLeft) { leftRef = reference; left = null } else { rightRef = reference; right = null }
        selectedChange = 0
        compute(reference to isLeft)
    }
    private class InputLimit : RuntimeException()
    private fun read(reference: ComparisonRef, cancellation: CancellationSignal): ComparisonSnapshot {
        val uri = reference.uri ?: throw IllegalArgumentException("Shared snapshot unavailable")
        val document = repository.read(Uri.parse(uri), null, cancellation, reference.encoding)
        cancellation.throwIfCanceled()
        if (document.bytes > LineDiff.MAX_BYTES || document.text.length > LineDiff.MAX_CHARS) throw InputLimit()
        return ComparisonSnapshot(reference.copy(name = document.name, fingerprint = sourceFingerprint(document.text)), document)
    }
    private fun compute(replacement: Pair<ComparisonRef, Boolean>? = null, restoring: Boolean = false) {
        val request = generation
        val cancellation = CancellationSignal().also { signal = it }
        val oldLeft = left; val oldRight = right
        val firstRef = leftRef; val secondRef = rightRef
        busy = true; failure = null; changedSources = false; result = null; listener?.invoke()
        work = worker.submit {
            try {
                var first = oldLeft; var second = oldRight
                if (restoring) {
                    first = firstRef?.takeIf { it.uri != null }?.let { read(it, cancellation) }
                    second = secondRef?.takeIf { it.uri != null }?.let { read(it, cancellation) }
                } else replacement?.let { (reference, isLeft) ->
                    val loaded = read(reference, cancellation)
                    if (isLeft) first = loaded else second = loaded
                }
                cancellation.throwIfCanceled()
                val changed = (firstRef?.fingerprint != null && first?.reference?.fingerprint != firstRef.fingerprint) ||
                    (secondRef?.fingerprint != null && second?.reference?.fingerprint != secondRef.fingerprint)
                val diff = if (first != null && second != null) LineDiff.compare(first!!.document, second!!.document) else null
                val firstValue = first; val secondValue = second
                handler.post {
                    if (request != generation) return@post
                    left = firstValue; right = secondValue
                    firstValue?.let { leftRef = it.reference }; secondValue?.let { rightRef = it.reference }
                    result = diff; changedSources = changed
                    if (changed) selectedChange = 0
                    if (restoring && (firstRef?.uri == null && firstRef != null || secondRef?.uri == null && secondRef != null)) failure = ComparisonFailure.LOST_SHARED
                    busy = false; listener?.invoke()
                }
            } catch (error: Exception) {
                if (error is java.util.concurrent.CancellationException || error is android.os.OperationCanceledException) return@submit
                handler.post {
                    if (request != generation) return@post
                    failure = if (error is InputLimit) ComparisonFailure.INPUT_LIMIT else ComparisonFailure.READ
                    busy = false; listener?.invoke()
                }
            }
        }
    }
    fun cancel() { stopWork(); busy = false; failure = ComparisonFailure.CANCELLED; listener?.invoke() }
    private fun stopWork() { generation++; signal?.cancel(); work?.cancel(true); signal = null }
    fun save(bundle: Bundle) {
        fun reference(prefix: String, value: ComparisonRef?) {
            if (value == null) return
            bundle.putBoolean(prefix + "Present", true); bundle.putString(prefix + "Uri", value.uri)
            bundle.putString(prefix + "Name", value.name); bundle.putString(prefix + "Encoding", value.encoding?.name)
            bundle.putString(prefix + "Fingerprint", value.fingerprint)
        }
        reference("left", leftRef); reference("right", rightRef)
        bundle.putInt("change", selectedChange)
    }
    fun restore(bundle: Bundle) {
        fun reference(prefix: String) = if (!bundle.getBoolean(prefix + "Present")) null else ComparisonRef(
            bundle.getString(prefix + "Uri"), bundle.getString(prefix + "Name").orEmpty(),
            TextEncoding.entries.find { it.name == bundle.getString(prefix + "Encoding") }, bundle.getString(prefix + "Fingerprint"))
        leftRef = reference("left"); rightRef = reference("right"); selectedChange = bundle.getInt("change")
        stopWork(); compute(restoring = true)
    }
    override fun onCleared() { listener = null; stopWork(); worker.shutdownNow(); left = null; right = null; result = null; super.onCleared() }
}
