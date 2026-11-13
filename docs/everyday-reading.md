# Search, text encoding and navigation

## Finding text

The magnifying glass opens **Find in this view**. It searches the text displayed by the WebView, with previous/next controls and a match count. Preview and source text can differ.

The **⋯** control in that Find bar opens **Search source…**. This searches the complete decoded source, regardless of display mode, with optional **Match case** and **Whole word**. Results show source line numbers and short snippets. Controls and results scroll together to remain reachable in landscape and at large font sizes. Selecting one switches to plain text, scrolls to the exact occurrence and highlights it. The decoration adds no characters to copied source. JavaScript remains disabled.

Source search is literal, not regular-expression search. Matches do not overlap. Case-insensitive matching compares Unicode code points by converting to uppercase and then lowercase, without normalization or multi-character expansions (for example, `ß` does not match `ss`). Whole-word boundaries treat Unicode letters, digits, combining marks and underscore as word characters. Line numbers recognize LF, CRLF and standalone CR. A query is limited to 1,000 UTF-16 code units; results stop at 200 and explicitly report when more exist. Refine the query to narrow an overflowing result list. Matching runs off the UI thread and is canceled when the query changes or dialog closes.

Source query/options stay in memory per open document, including across activity recreation. The source search dialog itself closes on recreation; reopen it to reuse those options. Queries are not stored in recent-file or open-document preferences and disappear after process death. The existing Find query retains its existing Android activity-state behavior. Neither search scans other files.

## Text encoding

Automatic decoding remains strict: UTF-8, UTF-8 with a byte-order mark (BOM), or UTF-16LE/BE with a BOM. Textr does not silently guess a legacy encoding or replace invalid bytes.

Choose **Document info → Reopen with encoding…** to interpret a file as UTF-8, Windows-1252, ISO-8859-1, UTF-16LE or UTF-16BE. Automatic restores the baseline behavior. Decoding/binary errors also offer this action, because a UTF-16 file without a BOM can initially look binary. A contradictory BOM or malformed byte sequence is rejected. Binary control-character checks and the 5 MiB byte limit still apply; selecting an encoding is not a binary-file viewer.

Reopening starts at the top because decoding can change layout. A successful choice is remembered for that URI in recent/open-document metadata and reused during reload, switching and restoration. A failed choice does not overwrite the last successful saved preference. Clearing recent history and closing documents retain the existing independent ownership rules. Incoming shared text is already decoded and has no encoding picker.

An override only changes interpretation. Share file and Open with still copy the original bytes, including their encoding, BOM and line endings.

## Document details and navigation

Document info shows the actual decoding charset, logical line count and line-ending types. Mixed endings list all present types; a document without separators shows “None.” An empty document has one logical line. This metadata is computed while loading, off the UI thread. **Treat as…** is also directly available in the reader menu.

The existing toolbar document count opens the switcher. A check mark and spoken “Current document” label identify the active entry. On screens at least 600 dp wide, with font scale at most 130%, two or more open documents also appear in a horizontally scrolling tab strip. Tabs have stable alphabetical order, full filename accessibility labels and tooltips. Larger font settings and phones use the compact switcher. The five-document limit and least-recently-used eviction policy are unchanged. Close actions remain in the switcher.

**Opening files and defaults** is available from the Home menu and **About Textr** in the reader. The offline help explains the picker, incoming/outgoing sharing, Android MIME/default-handler limitations, temporary access, encoding recovery and document-provider network access.

CSV table previews, bookmarks/progress, keyboard shortcuts and improved slow-loading feedback are tracked separately and are not implemented by this batch. Public release remains deferred.
