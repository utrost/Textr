# Textr privacy policy

Effective: 19 November 2026. Applies to the Textr Android app (`de.utrost.textr`), maintained by [utrost](https://github.com/utrost) in the [Textr project](https://github.com/utrost/Textr).

Textr is a read-only text viewer. It has no accounts, advertising, analytics, developer-operated backend, or Android network permission. Textr does not send your documents or usage information to its maintainer.

## Documents and local data

Files you choose are read and processed on your device. Textr does not edit them. Document contents remain in memory for viewing; recent files, open-document state and bookmarks do not retain those contents on disk.

Textr stores settings and references locally in its app-private storage: document URIs, display names, opening times, pinned status, approximate positions, display/format/encoding/CSV choices, global/per-document typography, and granted access references. Bookmark records also contain names you enter and a SHA-256 fingerprint of decoded source to detect changes. Search text is retained in memory for the current session, not saved with document history on disk. Up to 30 recent files, five open documents and 100 bookmarks are supported.

The app excludes its private data from Android cloud backup and device transfer. Its WebView disables JavaScript, DOM storage, network loading, remote images and file/content access; it does not intentionally cache documents for later reading. WebView's own app-private runtime files may be managed by Android.

## Explicit output documents

Save Markdown as creates a new user-selected file from the bounded HTML reading conversion. The original is not edited. Pending output content stays in a retained in-memory session. Private output-operation metadata contains the destination reference/name and outcome, with no document contents, so interrupted/confirmed writes can be reported after process death. Acknowledging the outcome clears that record. The app requests no broad storage access and retains no output write grant. Failed/cancelled output cleanup is best effort; an identified partial output may need removal through your file manager. Exported documents remain with the selected provider until you remove them.

## Sharing, copying, providers and links

Only your explicit Share file or Open with action creates a temporary app-private copy of the original bytes for another app, with read access. At most 20 copies are retained. Copies older than 24 hours are removed on the next Textr start or share, not by a background timer; Android may clear cache earlier. Short shared text can be sent directly as text, while larger shared text is shared as a UTF-8 file. Recipients can keep their own copies under their own policies.

Copying selected text places it on the Android clipboard, which is managed by Android and may be accessible to other apps subject to platform restrictions. Clearing Textr history does not clear the clipboard or recipient copies.

A document provider selected in Android’s picker may use its own network access to retrieve files. Textr’s lack of network permission does not prevent that provider from using the network. Tapped web/email links, source links, privacy links and issue links open in an external app. Those apps and websites have their own privacy practices; automatic remote resources in document previews are blocked.

Textr opts out of WebView usage metrics. Android/WebView crash diagnostics remain governed by device and provider settings; see [Android’s WebView privacy documentation](https://developer.android.com/develop/ui/views/layout/webapps/webview-privacy).

## Retention and removal

Recent references remain until removed, cleared, or displaced by the history limit. Removing a recent entry allows ten seconds of Undo. Closing open documents and clearing bookmarks are separate actions. Access grants are released when no retained history, session, bookmark or pending Undo entry needs them. These actions never delete the original documents.

Settings and remaining app-private data persist until you clear Textr’s storage in Android settings or uninstall it. This removes Textr’s local records and temporary share copies, but not original files, clipboard contents, or copies kept by recipients/providers. There is no server-side account to delete.

## Contact and changes

For privacy questions about Textr, contact the maintainer through the [project issue tracker](https://github.com/utrost/Textr/issues). Issues are public: describe the question without attaching private documents, personal data or credentials. See the [security reporting instructions](../SECURITY.md) for vulnerability reports.

Changes to this policy will be published here with an updated effective date. A future store listing must identify its accountable developer and contact details consistently with this policy before submission. No Play Store release is currently available.

## Document comparison

Comparison keeps at most two bounded decoded snapshots in memory. Saved activity state contains only URI/name/encoding references, content fingerprints and navigation positions. URI sources are reread after process loss; shared text is not persisted and must be selected again. Comparison never requests source writes or adds file contents to reading history.
