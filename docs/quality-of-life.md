# Everyday reading conveniences

Merged through PR #126 and included in the private 0.4-alpha candidate for #101, #109 and #120–#125. These additions are not included in the immutable 0.3-alpha APK. See [validation](validation-quality-of-life.md) and the [roadmap](roadmap.md).

## Find a recent file

The home screen offers **Search recent filenames**, a format filter and **Clear filters**. Name matching ignores case; format matching uses stored format overrides, the last detected format, or the filename for older entries. Filters combine, and pinned results stay first. An empty result explains that no files match. Filtering uses local history metadata without opening providers or searching document contents. Filters survive activity recreation within the session; clearing history also resets them.

## Return after a jump

**⋮ → Previous reading position** returns to the location before an outline/structure jump, source-search result, Find navigation or Go to line. It restores display mode, scroll position and search state. Repeated actions walk backward through up to 20 locations per document, across at most five documents. Ordinary scrolling does not add locations. The action is disabled without history.

This history survives activity recreation, but not process loss. Changed source fingerprints and explicit close invalidate it; positions are clamped to the current layout. Android Back retains its existing close-search/return-to-files behavior.

## Copy the HTML conversion

For a completed local/shared HTML conversion, **⋮ → Copy Markdown** copies the exact converted Markdown. **Copy all** continues to copy original source, including HTML tags. Conversion stays offline and follows the [HTML reading policy](html-reading.md).

Both clipboard actions accept at most 256 Ki UTF-16 characters. Oversized copies leave the existing clipboard untouched and explain the limit; use **Save Markdown as…** for a larger conversion. Failed/unavailable conversions cannot be copied. On Android 13+, Textr marks clipboard text sensitive to suppress its preview. The system clipboard and other apps retain their normal Android behavior.

## Preview an unsaved Markdown draft

In the copy editor, **Preview Markdown draft** renders an immutable snapshot of the current unsaved Markdown revision. The preview uses the existing bounded Markdown renderer, with JavaScript, remote resources, images and file/content access disabled. Supported links open externally only when tapped. **Back to editing** or Android Back returns to the unchanged buffer, selection, scroll position and undo history. Preview never saves an output or clears recovery.

Preview remains open across activity recreation, with its own scroll position. An obsolete revision is labeled stale; unavailable or over-budget rendering leaves the editable draft intact. Process loss still follows the existing private draft recovery workflow. Markdown classification is retained with recovery even when the provider’s filename has no Markdown extension. Older checkpoints infer it from the filename. Plain-text drafts have no Markdown preview button.

## Reopen an accidentally closed document

**⋮ → Reopen closed document** restores the most recently explicitly closed reader entry, including its mode, position and session search state. It differs from **Undo** removal from recent history. Up to five closed entries are kept in memory, with at most one shared-text snapshot. URI entries retain metadata only; closed history does not retain additional provider grants.

Reopening respects the five-open-document limit and reuses an already open entry without replacing its newer state. References are reread through normal access/recovery handling. Activity recreation preserves closed history; process loss clears it. Removing a recent reference forgets its closed entry, and clearing history clears all closed entries. Original files are never deleted.

## Keep the screen awake

Enable **Reading settings → Keep screen awake while reading** when following instructions or reading without touching the screen. It is off by default and remembers your preference. It keeps only a ready foreground reader awake; home, loading, errors and background activity clear the window flag. It uses no wake lock or service and does not prevent explicit device locking. Keeping the display on uses more battery.

## Find selected text

Long-press text in the reader and choose **Find selected text**, sometimes under the selection toolbar's overflow menu. It opens the existing **Search source** results with the exact selection and preserves case/whole-word options. Tap the query to type with the software keyboard. Query/options stay in place as asynchronous results change. Copy and Select all remain native actions. No clipboard round trip or JavaScript is used.

Search is literal against source. Preview text can differ from its markup—for example, styled text, normalized HTML whitespace or decoded entities—so a preview selection can have no exact source match. Whitespace-only selections and selections over the 1,000-character query budget are rejected without replacing the previous query. Queries stay session-only; reopening Search source after recreation retains the selected query.

The integration uses Android's `PROCESS_TEXT` action. WebView must support that native action; use the toolbar's ordinary **Search source** action if the selection action is unavailable, including unsupported native compatibility/preview-editor surfaces. The destination has a signature-protected permission and verifies the same-app caller and reader snapshot; it is not a general external text-processing service.

## Reuse a save location

After successful editor **Save as** or HTML **Save Markdown as**, the next system picker receives the last successful destination document URI as an initial-location hint. Android/providers may navigate to its parent, ignore the hint or fall back if it is unavailable. You still choose and confirm every destination; Textr does not derive filesystem paths from provider URIs.

Cancellation, rejected targets and failed/partial writes do not replace the previous hint. **Reading settings → Forget last save location** removes it. This preference contains one opaque URI, survives process recreation, and is excluded from backup with other app data. It retains no output write grant and stores no output content. Physical/cloud-provider behavior remains part of [private beta #56](https://github.com/utrost/Textr/issues/56).
