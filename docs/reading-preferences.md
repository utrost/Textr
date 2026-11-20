# Reading preferences

Settings provides text size (12–28), line wrapping, three Android system font families and line spacing (120–220%). Source and code always use monospace; the selected family applies to prose and native table cells. Android system text scaling applies in addition to the selected size. Theme, line numbers and the file sidebar remain global settings.

Without an override, a document follows the latest global defaults. With a document open, select **Only this document** before tapping **Done** to store its size, wrapping, font and spacing separately. Other documents keep their settings. **Use global reading defaults**, followed by **Done**, removes the override. Cancel makes no changes. When the document checkbox is clear, Done updates the global defaults.

Profiles contain preferences only. URI profiles live alongside recent/open-document references, use the same bounded lists and disappear when those references are removed. Bookmarks independently capture a profile as part of their reading-position snapshot and release it when the bookmark is removed. An unavailable URI does not prevent removing its metadata. Shared text and its profile stay in the live session; rotation preserves them, but process death does not retain shared contents or settings on disk. Existing installations without profiles continue using global defaults.

Changes retain the current source/mode and approximate pixel position. Reflow can change the visible passage; bookmarks and source navigation remain available. Font choices use installed system families, with no downloaded font resources.
