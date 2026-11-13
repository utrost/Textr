# CSV tables, bookmarks and keyboard shortcuts

Issues [#44](https://github.com/utrost/Textr/issues/44), [#45](https://github.com/utrost/Textr/issues/45) and [#46](https://github.com/utrost/Textr/issues/46) extend the read-only reader. Public release remains deferred.

## CSV tables

CSV opens as plain text. Choose **⋮ → CSV table options…**, select **Comma**, **Semicolon**, **Tab** or **Pipe**, and press **Preview**. There is no delimiter guessing. A fresh file defaults to comma; **Use first row as header** defaults off. Generated “Column 1” headings keep the first row as data unless explicitly enabled. Preferences are remembered with recent/open-document reading metadata. The display-mode control returns to exact source text or an available preview.

The parser supports double-quoted fields, doubled quotes inside them, embedded newlines, empty fields and LF/CRLF/CR record endings. Quotes must start at the beginning of a field. After a closing quote, only a delimiter, record ending or end of input is accepted. Spaces are significant; malformed quoting rejects the entire preview. Blank records represent one empty field. Ragged rows keep their original number of fields; absent trailing cells are not silently padded. A terminal record separator does not invent another record.

Preview limits: 256 Ki UTF-16 input characters, 1,000 records (including a selected header), 100 columns per record, 10,000 source cells, and 16,384 UTF-16 characters per field. All limits are inclusive. Over-budget or malformed input retains the complete source and displays an explanation; choosing another delimiter may resolve a parsing mismatch. Empty CSV has an empty table.

The table scrolls horizontally and vertically within the reader with sticky column headers. Semantic table/header/cell markup supports accessibility. Cells support native text selection/copy; copied cell text is decoded table content, while source view preserves CSV delimiters/quoting. **Share file** and **Open with** still send the original bytes, including BOM, encoding and line endings. HTML, formulas and links in cells remain inert text. No sorting, filtering, editing or spreadsheet calculation is provided.

## Reading progress and bookmarks

A small status line shows approximate vertical reading position, from 0% at the top to 100% at the bottom; a document that fits entirely shows 100%. It is based on the rendered scroll range, not pages, words read or time remaining. Font, viewport, mode and content changes can move the position. The status is accessible but does not announce every scroll. CSV tables have their own inner scrolling area, so the status directs readers to source view for progress and bookmarks instead of displaying a misleading percentage.

For a URI-backed file, choose **⋮ → Add bookmark…**, give it a name and save. Up to **100 bookmarks total** are supported, with names up to **80 UTF-16 code units**. Shared text without a reopenable URI and CSV table preview cannot create bookmarks; use a file/source view. **⋮ → Bookmarks** is available on both Home and the reader. Selecting a bookmark offers Open, Rename, Choose file and Delete; Clear bookmarks requires confirmation.

Bookmarks store a local file reference, filename, user-entered name, approximate fractional scroll position, display mode, format/encoding/CSV preferences and a SHA-256 fingerprint of decoded source. They never store document contents or excerpts. They survive app restarts independently of recent history and the five-document session. Clearing history or closing documents does not remove bookmarks. Persisted read permission is retained while history, session, Undo or bookmarks still own the reference; deleting the last owner releases it. Backup and device transfer exclusions cover bookmarks too. Clearing app data/uninstalling removes them.

Opening a bookmark rereads the current file and clamps its approximate position to the current layout. A changed source fingerprint triggers an explanation; bookmarks do not track semantic paragraphs or relocate exact text. Unavailable files keep their bookmarks and offer the picker to reconnect. **Choose file for bookmark…** can reconnect an individual bookmark to a selected file, with the association updated only after a successful read. The user chooses the replacement; filenames are not treated as identity. Cancellation or a failed read keeps the old association. Other bookmarks to the old file are unchanged. Changed content remains flagged until the user creates a new bookmark.

## Hardware keyboard

| Shortcut | Action |
| --- | --- |
| Ctrl+O | Open file picker |
| Ctrl+F | Find in the current view |
| Ctrl+G | Go to line in source view |
| Ctrl+Tab | Next open document |
| Ctrl+Shift+Tab | Previous open document |

Switching follows the tab strip’s stable alphabetical filename order (file reference breaks ties) and wraps. Reader shortcuts require a loaded document; Go to line is unavailable in previews. Text fields, dialogs and menus retain control of their shortcuts; Alt/Meta combinations and standard copy/selection shortcuts are not intercepted. Holding a shortcut does not repeatedly launch actions. All actions remain available through touch controls.

Discover the list in **About Textr → Keyboard shortcuts**, or Android's keyboard shortcut helper. Dispatch and helper integration use the [Android hardware-input APIs](https://developer.android.com/develop/ui/views/touch-and-input/input-compatibility-on-large-screens).
