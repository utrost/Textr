# Opening and recovery

These actions are included in 0.3-alpha. The immutable 0.2-alpha APK predates them.

When opening fails, Textr distinguishes denied access, a file the provider could not open, a stopped provider and an unknown access failure. A provider's file-not-found response can also mean temporary unavailability; Textr does not claim that the file was definitely deleted. Format, size and decoding errors retain their specific explanations and encoding recovery.

- **Retry opening** retries the same reference and reading settings after the provider or permission is restored.
- **Open file again** opens the system picker. Cancelling leaves the current recovery screen intact. Selecting a different URI uses that document's own settings; it does not transfer another file's history or recovery draft.
- **Return to previous document** restores the last ready reader snapshot, including its mode, position and search. This works even if the original provider is now unavailable. Cancelling a replacement load also returns to that snapshot, and late results from cancelled work cannot replace it. Without a previous snapshot, return goes home.

The return snapshot is bounded by the existing reader limits and exists only in the current reader session's memory. It survives activity recreation, not process termination, and is released on returning home or replaced when the next document becomes ready. It is not an additional recovery file or a new recent-history content store.

Reopening an unavailable bookmark with a different URI asks whether to **Reconnect bookmark**, **Open separately**, or **Cancel**. Only explicit reconnection applies that bookmark and its approximate position to the selected document; the stored bookmark changes only after the file opens successfully. Other bookmarks and private editor drafts remain unchanged.

Failed output explains denied permission, an unavailable destination or an incompatible encoding when the platform provides that evidence. **Save a new copy** opens the existing save flow again with an explicitly chosen destination; it never blindly retries a partly written target. Partial-output warnings and cleanup behavior still apply. A cancelled, failed or interrupted save keeps the editor's recovery checkpoint. Only a confirmed successful save of the matching draft and revision can remove it.

In the editor, **Retry private recovery** retries a failed checkpoint without requiring another edit. If a recovery file cannot be read, retry attempts to read it again; returning to files keeps it, and Discard requires confirmation. Keep the editor open and use Save a new copy if private storage continues to fail. Retry cannot repair a genuinely corrupt file or restore keystrokes that were never checkpointed.

For validation evidence and remaining physical-device/TalkBack work, see the [reliability validation record](validation-reliability.md). See also [copy editing](editing-copies.md), [output behavior](save-as.md), and [reading extras](reading-extras.md).
