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
/** One immutable pair, transferred only in memory; never reread a draft baseline. */
data class DraftComparison(val first: ComparisonSnapshot, val second: ComparisonSnapshot, val sourceUri: String?)
object DraftComparisonInbox {
    private var pending: Pair<String, DraftComparison>? = null
    @Synchronized fun put(name: String, original: String, draft: String, sourceUri: String? = null,
        originalEncoding: String = "UTF-8", originalBom: Boolean = false, draftEncoding: String = originalEncoding, draftBom: Boolean = originalBom): String? {
        fun snapshot(text: String, encoding: String, bom: Boolean) = ComparisonSnapshot(ComparisonRef(null, name, fingerprint = sourceFingerprint(text)),
            TextDocument(name, text, FileKind.TEXT, text.toByteArray(Charsets.UTF_8).size, encoding, hasBom = bom))
        if (original.length > LineDiff.MAX_CHARS || draft.length > LineDiff.MAX_CHARS) return null
        val left = snapshot(original, originalEncoding, originalBom); val right = snapshot(draft, draftEncoding, draftBom)
        if (left.document.bytes > LineDiff.MAX_BYTES || right.document.bytes > LineDiff.MAX_BYTES) return null
        return UUID.randomUUID().toString().also { pending = it to DraftComparison(left, right, sourceUri) }
    }
    @Synchronized fun take(token: String?): DraftComparison? =
        pending?.takeIf { it.first == token }?.also { pending = null }?.second
}
enum class ComparisonFailure { READ, INPUT_LIMIT, CANCELLED, LOST_SHARED, LOST_DRAFT, EXPORT_LIMIT }

class ComparisonSession(application: Application) : AndroidViewModel(application) {
    private val repository = DocumentRepository(application)
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var work: Future<*>? = null
    private var signal: CancellationSignal? = null
    private var generation = 0
    private var draftSourceUri: String? = null
    fun protectedSources(): Set<String> = setOfNotNull(leftRef?.uri, rightRef?.uri, draftSourceUri)
    var draftMode = false; private set
    var options = DiffOptions(); private set
    private var exported: String? = null
    fun takeExport(): String? = exported.also { exported = null }
    fun setOptions(value: DiffOptions) {
        if (busy || options == value) return
        options = value; selectedChange = 0; stopWork(); compute()
    }
    fun initialDraft(pair: DraftComparison) {
        stopWork(); draftMode = true; draftSourceUri = pair.sourceUri; left = pair.first; right = pair.second
        leftRef = left!!.reference; rightRef = right!!.reference; selectedChange = 0; compute()
    }
    fun exportExact() {
        if (busy) return
        val first = left ?: return; val second = right ?: return
        stopWork(); val request = generation
        busy = true; failure = null; exported = null; listener?.invoke()
        work = worker.submit {
            try {
                val patch = UnifiedDiff.export(first.document, second.document)
                handler.post {
                    if (generation == request) { busy = false; exported = patch; if (patch == null) failure = ComparisonFailure.EXPORT_LIMIT; listener?.invoke() }
                }
            } catch (error: Exception) {
                if (error is java.util.concurrent.CancellationException) return@submit
                handler.post { if (generation == request) { busy = false; failure = ComparisonFailure.EXPORT_LIMIT; listener?.invoke() } }
            }
        }
    }
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

    fun initial(snapshot: ComparisonSnapshot) { stopWork(); if (draftMode) { right = null; rightRef = null }; draftMode = false; draftSourceUri = null; left = snapshot; leftRef = snapshot.reference; selectedChange = 0; compute() }
    fun load(isLeft: Boolean, reference: ComparisonRef) {
        if (draftMode) return
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
        val filters = options
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
                val diff = if (first != null && second != null) LineDiff.compare(first!!.document, second!!.document, filters) else null
                val firstValue = first; val secondValue = second
                handler.post {
                    if (request != generation) return@post
                    left = firstValue; right = secondValue
                    firstValue?.let { leftRef = it.reference }; secondValue?.let { rightRef = it.reference }
                    result = diff; changedSources = changed
                    if (changed) selectedChange = 0
                    if (restoring && (firstRef?.uri == null && firstRef != null || secondRef?.uri == null && secondRef != null)) failure = if (draftMode) ComparisonFailure.LOST_DRAFT else ComparisonFailure.LOST_SHARED
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
        bundle.putString("draftSourceUri", draftSourceUri); bundle.putBoolean("draftMode", draftMode); bundle.putBoolean("ignoreWhitespace", options.ignoreWhitespace); bundle.putBoolean("ignoreEndings", options.ignoreLineEndings)
    }
    fun restore(bundle: Bundle) {
        fun reference(prefix: String) = if (!bundle.getBoolean(prefix + "Present")) null else ComparisonRef(
            bundle.getString(prefix + "Uri"), bundle.getString(prefix + "Name").orEmpty(),
            TextEncoding.entries.find { it.name == bundle.getString(prefix + "Encoding") }, bundle.getString(prefix + "Fingerprint"))
        draftMode = bundle.getBoolean("draftMode"); draftSourceUri = bundle.getString("draftSourceUri")
        options = DiffOptions(bundle.getBoolean("ignoreWhitespace"), bundle.getBoolean("ignoreEndings"))
        leftRef = reference("left"); rightRef = reference("right"); selectedChange = bundle.getInt("change")
        stopWork(); compute(restoring = true)
    }
    override fun onCleared() { listener = null; stopWork(); worker.shutdownNow(); left = null; right = null; result = null; super.onCleared() }
}
