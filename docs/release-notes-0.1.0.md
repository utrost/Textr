# Textr 0.1.0

First release candidate of a minimal, read-only Android text viewer. Public release is deferred at the owner's request. The [roadmap](roadmap.md) retains production signing and publication as follow-up work.

- Android 10 and newer; UTF-8 and BOM-marked UTF-16 files up to 5 MiB.
- Open files through the system picker, compatible Open with flows, or attachment sharing.
- Plain text, lightweight syntax highlighting, Markdown previews, and indented JSON.
- Search, text selection/copying, text size, wrapping, and light/dark/system appearance.
- Local recent references with individual removal and history clearing.
- No editing, accounts, ads, analytics, network permission, or broad storage permission.

Default associations depend on the sending app and MIME type. Markdown preview has a 512 Ki-character input limit, 20,000-node limit, and nesting limit of 64. JSON preview accepts files up to 1 MiB and has a nesting limit of 128 and formatted-output limit of 5 Mi characters. Source remains readable when previews are unavailable; highlighting accepts up to 512 Ki characters and decorates up to 10,000 tokens. Images, embedded HTML, relative file links, huge logs, tabs, and file management are outside this version.

Validation uses clean API 29, 35, and 36 emulators as requested by the owner. Physical-device coverage is a follow-up. See [installation and defaults](installing.md), [development](development.md), and [release signing](releasing.md).

License: GPL-3.0-only. Dependency notices are bundled in the app and source.
