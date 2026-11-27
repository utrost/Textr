package de.utrost.textr

data class NavigationPosition(val mode: DisplayMode, val scroll: Int, val query: String = "", val searching: Boolean = false,
    val sourceSearch: SourceSearchOptions = SourceSearchOptions())

/** Session-only, bounded locations; no source contents or persisted search queries. */
class ReadingNavigation {
    private class History(val fingerprint: String) {
        val previous = ArrayDeque<NavigationPosition>()
        val next = ArrayDeque<NavigationPosition>()
    }
    private val documents = linkedMapOf<String, History>()
    fun bind(key: String, fingerprint: String) {
        val previous = documents.remove(key)
        documents[key] = previous?.takeIf { it.fingerprint == fingerprint } ?: History(fingerprint)
        while (documents.size > 5) documents.remove(documents.keys.first())
    }
    fun push(key: String, position: NavigationPosition) {
        val history = documents[key] ?: return
        history.next.clear()
        if (history.previous.lastOrNull() != position) history.previous.addLast(position)
        if (history.previous.size > 20) history.previous.removeFirst()
    }
    fun hasPrevious(key: String) = documents[key]?.previous?.isNotEmpty() == true
    fun hasNext(key: String) = documents[key]?.next?.isNotEmpty() == true
    fun move(key: String, current: NavigationPosition, forward: Boolean): NavigationPosition? {
        val history = documents[key] ?: return null
        val from = if (forward) history.next else history.previous
        val to = if (forward) history.previous else history.next
        val destination = from.removeLastOrNull() ?: return null
        to.addLast(current)
        return destination
    }
    fun clear(key: String) { documents.remove(key) }
}

object RecentFiltering {
    fun filter(files: List<RecentFile>, query: String, kind: FileKind?): List<RecentFile> = files.filter {
        it.name.contains(query, ignoreCase = true) && (kind == null || (it.reading.format ?: it.knownFormat ?: TextDocuments.kind(it.name, null)) == kind)
    }.sortedWith(compareByDescending<RecentFile> { it.pinned }.thenByDescending { it.openedAt })
}
