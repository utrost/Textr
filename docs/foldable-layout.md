# Optional layout for foldables and tablets

Enable **More options → Reading settings → File sidebar on wide screens**, then tap **Done**. The option is off by default and is saved locally. It can also be enabled on the cover screen before unfolding.

While a document is open, the sidebar offers **Open file**, the current five-document session with switch/close actions, and recent files that are not already open. Pinned recents come first. The active document has a check mark and a spoken “Current document” label. Long filenames wrap to two lines, with full accessibility labels and tooltips. Sidebar lists scroll independently of the reader. Closing the current document returns to the home screen; closing another leaves the reader open. History removal and pinning remain on the home screen.

The sidebar uses the app's available space, not a device name or physical screen resolution. It appears when the inset-adjusted window is at least 600 dp wide and 480 dp tall, with system font scale at most 130%. The sidebar is 220 dp wide, leaving at least 380 dp for reading at the width threshold. It automatically hides on smaller windows, short landscape windows, at larger system text sizes, and on the home screen. Display-size settings, split-screen, and the keyboard can reduce the available space. Android's density-independent width can differ between devices with similar physical screen sizes.

When the sidebar is hidden, the existing compact document switcher and eligible wide-screen tab strip remain available. The tab strip is hidden while the sidebar is visible. The file limit, URI permissions, local history, and offline/read-only behavior are unchanged.

Folding, unfolding, rotation, and window resizing use Android's normal activity recreation and Textr's existing restoration of the active document, display mode, Find query, and vertical scroll offset. Scroll restoration remains approximate when reflow changes line positions; this is not a semantic bookmark. No second document or source/preview comparison is displayed. The layout does not use hinge-angle or separating-hinge information and is intended for a continuous unfolded display; tabletop and dual-screen hinge-specific layouts remain follow-ups.

This follows Android's recommendation to [adapt to the available app-window size](https://developer.android.com/develop/ui/views/layout/use-window-size-classes). Emulator resize tests cover compact/wide transitions and enlarged text; they do not establish physical HONOR Magic V6 compatibility or manufacturer-specific cover-screen handoff behavior.
