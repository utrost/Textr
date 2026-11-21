package de.utrost.textr

data class NavigationPosition(val mode: DisplayMode, val scroll: Int, val query: String = "", val searching: Boolean = false,
    val sourceSearch: SourceSearchOptions = SourceSearchOptions())

/** Session-only, bounded locations; no source contents or persisted search queries. */
class ReadingNavigation {
    private data class History(val fingerprint: String, val positions: ArrayDeque<NavigationPosition> = ArrayDeque())
    private val documents = linkedMapOf<String, History>()
    fun bind(key: String, fingerprint: String) {
        if (documents[key]?.fingerprint != fingerprint) documents[key] = History(fingerprint)
        while (documents.size > 5) documents.remove(documents.keys.first())
    }
    fun push(key: String, position: NavigationPosition) {
        val positions = documents[key]?.positions ?: return
        if (positions.lastOrNull() != position) positions.addLast(position)
        while (positions.size > 20) positions.removeFirst()
    }
    fun hasPrevious(key: String) = documents[key]?.positions?.isNotEmpty() == true
    fun pop(key: String) = documents[key]?.positions?.removeLastOrNull()
    fun clear(key: String) { documents.remove(key) }
}

object RecentFiltering {
    fun filter(files: List<RecentFile>, query: String, kind: FileKind?): List<RecentFile> = files.filter {
        it.name.contains(query, ignoreCase = true) && (kind == null || (it.reading.format ?: TextDocuments.kind(it.name, null)) == kind)
    }.sortedWith(compareByDescending<RecentFile> { it.pinned }.thenByDescending { it.openedAt })
}
