# Contributing to Textr

Textr is a small offline Android reader and inspector, with explicit editing of separate TXT/Markdown copies. Start with the [README](README.md), [scope](docs/scope.md) and [roadmap](docs/roadmap.md). Public app release is deferred; contributions do not imply a release commitment.

For a bug, include Android version, device/emulator, Android System WebView version, how the file was opened, selected display mode and exact steps. Attach a minimal synthetic example only when useful. Never upload private documents, credentials, signing files or provider tokens. For new features, explain the reading problem before proposing extra UI.

Use JDK 17 and SDK 36. Run the build, unit tests and lint described in [development](docs/development.md); run relevant instrumentation on an emulator for UI/lifecycle/provider changes. The CI matrix uses APIs 29 and 36. Keep English and German strings aligned. Generated preview labels belong in the translated `preview_labels` arrays; document contents and format names must remain intact.

Preserve read-only original documents, original bytes during sharing, the 5 MiB source-reading limit and the absence of network permission. Export and copy editing create new destinations; keep the [editor budgets, source guards and private-recovery contract](docs/editing-copies.md) intact. Avoid secrets and dependency changes unrelated to the task. Keep screenshots synthetic and record their capture configuration in [screenshots](docs/screenshots.md).

Open a pull request describing the user-visible change, linked issues, validation and remaining limits. Contributions are distributed under this repository’s GPL-3.0-only license. Do not add a CLA or change the license without maintainer agreement.
