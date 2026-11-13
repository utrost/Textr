package de.utrost.textr

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.CancellationSignal
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

/** Copies exist only for explicit sharing, never as recent-file or reading caches. */
class ShareFiles(private val context: Context) {
    private val directory get() = File(context.cacheDir, "shared")

    fun prune(now: Long = System.currentTimeMillis()) {
        directory.listFiles()?.filter { now - it.lastModified() > MAX_AGE_MS }?.forEach { it.deleteRecursively() }
    }

    fun prepare(source: Uri?, document: TextDocument, signal: CancellationSignal): Intent {
        prune()
        // Bound temporary storage, including rapid repeated shares within the retention period.
        val previous = directory.listFiles().orEmpty().sortedBy { it.lastModified() }
        previous.take((previous.size - MAX_COPIES + 1).coerceAtLeast(0)).forEach { it.deleteRecursively() }
        val folder = File(directory, UUID.randomUUID().toString())
        check(folder.mkdirs())
        val name = document.name.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[\\p{Cntrl}]"), "_").takeUnless { it.isBlank() || it == "." || it == ".." } ?: "Shared text.txt"
        // Providers may return names longer than a local filesystem permits. Keep
        // their display name in metadata without using it as an unsafe cache path.
        val file = File(folder, name.takeIf { it.toByteArray(Charsets.UTF_8).size <= 240 } ?: "document")
        try {
            file.outputStream().use { output ->
                if (source != null) DocumentRepository(context).copySource(source, output, signal)
                else {
                    signal.throwIfCanceled()
                    output.write(document.text.toByteArray(Charsets.UTF_8))
                }
            }
            signal.throwIfCanceled()
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.shared", file, name)
            return Intent(Intent.ACTION_SEND).apply {
                type = mime(document.name, document.kind)
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri(document.name, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (error: Exception) {
            folder.deleteRecursively()
            throw error
        }
    }

    companion object {
        const val MAX_AGE_MS = 24 * 60 * 60 * 1000L
        const val MAX_COPIES = 20
        fun mime(name: String, kind: FileKind): String = when {
            name.endsWith(".csv", ignoreCase = true) -> "text/csv"
            else -> when (kind) {
                FileKind.MARKDOWN -> "text/markdown"
                FileKind.JSON -> "application/json"
                FileKind.XML -> "application/xml"
                FileKind.YAML -> "application/yaml"
                FileKind.TOML -> "application/toml"
                else -> "text/plain"
            }
        }
    }
}
