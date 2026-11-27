package de.utrost.textr

import org.junit.Assert.*
import org.junit.Test

class QualityOfLifeTest {
    @Test fun navigationIsBoundedIndependentAndInvalidatedByContentChanges() {
        val history = ReadingNavigation()
        val current = NavigationPosition(DisplayMode.PLAIN, 100)
        fun back(key: String) = history.move(key, current, forward = false)
        history.bind("a", "original"); history.bind("b", "original")
        for (i in 0..25) history.push("a", NavigationPosition(DisplayMode.PLAIN, i))
        val search = NavigationPosition(DisplayMode.PREVIEW, 50, "Grüße", true, SourceSearchOptions("needle", true, true))
        history.push("b", search); history.push("b", search)
        history.bind("b", "original")
        assertEquals(search, back("b")); assertNull(back("b"))
        assertEquals((25 downTo 6).toList(), generateSequence { back("a") }.map { it.scroll }.toList())
        history.push("a", search); history.bind("a", "edited")
        assertFalse(history.hasPrevious("a"))
        history.push("a", search); history.clear("a"); assertNull(back("a"))
        for (i in 0..5) { history.bind("$i", "same"); history.push("$i", search) }
        assertNull(back("0")); assertEquals(search, back("5"))
        history.bind("1", "same") // Reopening an existing document refreshes recency.
        history.bind("6", "same")
        assertEquals(search, back("1")); assertNull(back("2"))
    }
    @Test fun navigationRoundTripsAndDropsForwardHistoryOnANewJump() {
        val history = ReadingNavigation()
        val start = NavigationPosition(DisplayMode.PLAIN, 200)
        val end = NavigationPosition(DisplayMode.PREVIEW, 700, "heading", true)
        history.bind("file", "same")
        history.push("file", start)
        assertEquals(start, history.move("file", end, forward = false))
        assertTrue(history.hasNext("file"))
        assertEquals(end, history.move("file", start, forward = true))
        assertFalse(history.hasNext("file"))
        assertEquals(start, history.move("file", end, forward = false))
        history.push("file", start)
        assertNull(history.move("file", start, forward = true))
    }

    @Test fun exportSuggestionsUseDisplayNamesAndKeepTheCorrectExtension() {
        assertEquals("meeting-notes-copy.md", OutputNames.copy("meeting-notes", markdown = true))
        assertEquals("article.md", OutputNames.markdown("folder/article.HTML"))
        assertEquals("config-changes.patch", OutputNames.patch("config.ini"))
        assertEquals("changes.patch", OutputNames.patch(null))
        assertEquals("report_-copy.txt", OutputNames.copy("C:\\fake\\report?.txt", markdown = false))
        assertEquals("document.md", OutputNames.markdown(".."))
        val longName = "a".repeat(59) + "🚀.txt"
        assertEquals("a".repeat(59) + "-copy.txt", OutputNames.copy(longName, markdown = false))
    }

    @Test fun recentFiltersCombineUnicodeNameAndKnownFormatWithoutChangingPinnedOrder() {
        val files = listOf(
            RecentFile("1", "GRÜSSE.md", 3),
            RecentFile("2", "Grüsse.txt", 5),
            RecentFile("3", "Grüsse.data", 1, pinned = true, knownFormat = FileKind.MARKDOWN),
            RecentFile("4", "other.md", 4))
        assertEquals(listOf("3", "1"), RecentFiltering.filter(files, "grüsse", FileKind.MARKDOWN).map { it.uri })
        assertEquals(listOf("3", "2", "4", "1"), RecentFiltering.filter(files, "", null).map { it.uri })
        assertTrue(RecentFiltering.filter(files, "absent", null).isEmpty())
        assertEquals(4, files.size)
        assertEquals(listOf("3"), RecentFiltering.filter(listOf(files[2].copy(reading = ReadingState(format = FileKind.JSON))), "", FileKind.JSON).map { it.uri })
    }
}
