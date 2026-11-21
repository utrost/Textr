package de.utrost.textr

import org.junit.Assert.*
import org.junit.Test

class QualityOfLifeTest {
    @Test fun navigationIsBoundedIndependentAndInvalidatedByContentChanges() {
        val history = ReadingNavigation()
        history.bind("a", "original"); history.bind("b", "original")
        for (i in 0..25) history.push("a", NavigationPosition(DisplayMode.PLAIN, i))
        val search = NavigationPosition(DisplayMode.PREVIEW, 50, "Grüße", true, SourceSearchOptions("needle", true, true))
        history.push("b", search); history.push("b", search)
        history.bind("b", "original")
        assertEquals(search, history.pop("b")); assertNull(history.pop("b"))
        assertEquals((25 downTo 6).toList(), generateSequence { history.pop("a") }.map { it.scroll }.toList())
        history.push("a", search); history.bind("a", "edited")
        assertFalse(history.hasPrevious("a"))
        history.push("a", search); history.clear("a"); assertNull(history.pop("a"))
        for (i in 0..5) { history.bind("$i", "same"); history.push("$i", search) }
        assertNull(history.pop("0")); assertEquals(search, history.pop("5"))
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
