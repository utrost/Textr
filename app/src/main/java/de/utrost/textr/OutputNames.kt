package de.utrost.textr

/** Suggestions for the system picker, using only the document's display name. */
object OutputNames {
    private val unsafe = Regex("""[\p{Cc}\p{Cf}<>:"/\\|?*]""")

    fun copy(name: String, markdown: Boolean) = stem(name) + if (markdown) "-copy.md" else "-copy.txt"
    fun markdown(name: String) = stem(name) + ".md"
    fun patch(name: String?) = name?.let(::stem)?.let { "$it-changes.patch" } ?: "changes.patch"

    private fun stem(name: String): String {
        val leaf = name.substringAfterLast('/').substringAfterLast('\\').trim()
        val extension = leaf.lastIndexOf('.')
        val safe = (if (extension > 0) leaf.substring(0, extension) else leaf)
            .replace(unsafe, "_").trim { it.isWhitespace() || it == '.' }
        // Bound the whole suggestion and keep surrogate pairs intact.
        val end = if (safe.length > 60 && safe[59].isHighSurrogate()) 59 else minOf(safe.length, 60)
        return safe.substring(0, end).trimEnd { it.isWhitespace() || it == '.' }.ifEmpty { "document" }
    }
}
