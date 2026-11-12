# Textr 0.1.0 validation

This record describes development-build validation on 2026-11-12. Public release and selection of a production signing identity are deferred at the owner's request. No physical device was available; the owner selected emulator validation.

## Environments

| Android | API | System image | WebView |
| --- | --- | --- | --- |
| 10 | 29 | Google APIs x86_64 | 74.0.3729.185 |
| 15 | 35 | Google Play x86_64 | 124.0.6367.219 |
| 16 | 36 | Google APIs x86_64 | 133.0.6943.137 |

Local emulators used a Pixel 2 profile, 1080 × 1920 display, density 420, 2 GiB RAM, two virtual cores, and software graphics. The final Android 15 suite additionally used 720 × 1280 at density 320 (360 dp portrait width), including 200% font size and landscape. These are compatibility checks, not predictions of performance on physical phones. CI independently runs API 29 and 36. Build requirements and reproducible commands are in [development](development.md).

## Automated results

The suite contains 17 JVM tests and 33 instrumentation tests. Local validation passed all cases on API 29, 35, and 36, combining full-suite runs with focused reruns after correcting test-harness synchronization with accessibility activation, floating selection menus, and the system picker's remembered folder. The application code remained unchanged during those harness corrections.

The [implementation PR checks](https://github.com/utrost/Textr/pull/23/checks) run the complete API 29/36 suites independently, plus build, JVM tests, and lint. Lint reports zero errors; its 15 advisory warnings concern newer dependency versions and optional Kotlin extension syntax. No warnings were suppressed to obtain a passing result.

An additional Android 15 check opened and searched the complete 5 MiB source with wrapping disabled (12,026 ms opening, 664 ms end-marker search). The TalkBack acceptance run at 360 dp and 200% fonts verified a running touch-exploration service rather than merely a saved enabled-service setting.

## File opening and defaults

The instrumentation matrix launches real implicit VIEW and SEND intents against a separate read-only provider. Every route must resolve to Textr, open the requested document, and finish WebView layout.

| File | Reported MIME type | VIEW | Attachment SEND |
| --- | --- | --- | --- |
| TXT / LOG | `text/plain` | Covered | Covered |
| CONF / CFG | `application/octet-stream` | Covered | Covered |
| INI | `application/ini` | Covered | Covered |
| JSON | `application/json` | Covered | Covered |
| Markdown | `text/markdown` | Covered | Covered |
| YAML | `application/yaml` | Covered | Covered |
| TOML | `application/toml` | Covered | Covered |
| XML | `application/xml` | Covered | Covered |
| CSV | `text/csv` | Covered | Covered |
| `UPPER.JSON` | `application/octet-stream` | Covered | Covered |
| Extensionless JSON | `application/json` | Covered | Covered |
| TXT with missing metadata/type | None | Covered | No reliable Android SEND contract |

Additional checks cover ClipData-only attachments, shared text, omitted metadata columns, null metadata, providers rejecting optional metadata queries, and denied/unreadable content.

Manual Android 15 checks used official upstream releases of [Fossify File Manager 1.6.4](https://github.com/FossifyOrg/File-Manager/releases/tag/1.6.4) and [Markor 2026.10.0](https://github.com/gsantner/markor/releases/tag/v2026.10.0):

- Fossify's actual file-opening UI opened TXT, LOG, CONF, CFG, INI, JSON, Markdown, YAML, TOML, XML, CSV, uppercase JSON, and an extensionless text file in Textr. A binary fixture produced Textr's explanatory error.
- Android's JSON chooser offered Textr and **Just once / Always**. Selecting **Always**, leaving the app, and reopening JSON from Fossify bypassed the chooser and displayed the selected file.
- Markor's **Share → File** sent a Markdown attachment through Android's sharesheet to Textr. The rendered headings, list, table, quote, and code block were checked.
- Android 16's system picker opened Markdown from the internal-storage provider. The read grant was persisted. The same recent file reopened after force-stop/relaunch and after actual process termination with its task retained. Moving the fixture caused an unavailable-file message; **Open file again** and reselection recovered it.

These results apply to those senders and MIME types. Android associations are not global filename-extension registrations. Files copied into an emulator with ADB may appear under the picker's internal-storage provider before the Downloads provider indexes them.

## Reader, lifecycle, and history

Instrumentation checks actual mode changes, invalid-JSON fallback, forward/backward search, no results, closing search, real long-press copying, font/wrap/theme preferences, rotation, scroll restoration, URI reload without an in-memory ViewModel, picker cancellation, and delivery of a new file to an existing reader. Rotation during loading has a dedicated regression: the new document must still select its automatic preview.

History tests exercise the 30-entry limit, deduplication, eviction, individual removal, clearing, real persisted grants, temporary grants, revoked access, and rejected binary/oversized files. References and metadata are retained; document contents are not. Removing history releases retained access and leaves source documents intact.

## Accessibility

TalkBack 15.0.0.639625893 ran on the Android 15 emulator, including a successful acceptance run at 360 dp portrait width and 200% font size. The accessibility test connects with `FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES`, preserving the running service, and checks focus/activation for mode selection, search, next/previous matches, settings, history, the picker, and unavailable-file recovery. Document text and the **Match 1 of 1** announcement label are exposed in the accessibility tree. Keyboard focus traversal and recovery-button focus were also inspected with TalkBack enabled.

Automated layout checks use 200% system font size in portrait and landscape, check visible controls for clipping, enforce 48 dp button targets, and verify that the reader and settings confirmation remain reachable. Search uses a polite live region. Emulator checks inspect focus and accessibility labels; they do not substitute for a human evaluation of synthesized speech or physical touch ergonomics.

Calculated contrast against the actual reader backgrounds:

| Text style | Light | Dark |
| --- | ---: | ---: |
| Body | 14.32:1 | 14.01:1 |
| Links / keys | 7.38:1 | 9.54:1 |
| Strings | 6.09:1 | 9.81:1 |
| Numbers / literals | 7.04:1 | 9.01:1 |
| Comments | 4.64:1 | 7.93:1 |

## Reliability and privacy

The fixture generator and test provider include exact-limit and oversized files, binary content, deeply nested JSON, large Markdown, dense JSON tokens, and large tables. Optional preview/highlighting limits preserve the complete plain-text source. A canceled provider pipe stops promptly; a superseded load cannot replace the new file or enter history. Tests terminate a real WebView renderer and verify successful plain-text recovery.

Hostile Markdown checks cover scripts, embedded HTML, frames, remote images, unsafe links, and automatic navigation. External links open only after an actual tap. JavaScript, resource loading, network permission, and broad storage permission are absent/disabled as appropriate. The test provider rejects writes; source bytes remain unchanged. A unique shared-text marker is absent from private files, caches, and preferences after use. Android/WebView crash diagnostics remain subject to platform settings as described in the README.

## Rendering measurements

One full Android 10 suite run produced these measurements with the software-rendered emulator above. Opening ends at the WebView visual-state callback, not merely at completion of file I/O. Search must find the final marker, proving that source was not truncated.

| Fixture | Opening (ms) | End-marker search (ms) | Peak app PSS (KiB) |
| --- | ---: | ---: | ---: |
| 5 MiB unbroken text | 16,335 | 742 | 133,446 |
| 5 MiB Markdown, source fallback | 17,952 | 794 | 148,709 |
| JSON nested 200 levels, source fallback | 914 | 22 | 114,524 |
| Approximately 5 MiB dense JSON tokens | 19,312 | 859 | 122,038 |
| Markdown with 12,000 table rows | 1,652 | 64 | 124,396 |
| 5 MiB repeated ampersands | 64,639 | 757 | 141,755 |

For comparison, the Android 15 / WebView 124 run at 360 dp portrait width measured:

| Fixture | Opening (ms) | End-marker search (ms) | Peak app PSS (KiB) |
| --- | ---: | ---: | ---: |
| 5 MiB unbroken text | 5,441 | 630 | 159,816 |
| 5 MiB Markdown, source fallback | 7,216 | 627 | 160,502 |
| JSON nested 200 levels, source fallback | 1,350 | 46 | 119,322 |
| Approximately 5 MiB dense JSON tokens | 8,684 | 682 | 151,097 |
| Markdown with 12,000 table rows | 2,171 | 53 | 117,436 |
| 5 MiB repeated ampersands | 10,704 | 635 | 145,531 |

PSS is sampled every 200 ms in the application process; it excludes the separate WebView renderer and is not a heap-allocation total. Timings are single-run observations affected by emulator and host load. Large uninterrupted punctuation runs are deliberately pathological and can take over a minute on the old WebView. The toolbar remains available while the renderer works. Source streaming avoids allocating the whole escaped HTML expansion; safe soft breaks bound punctuation layout work. A regression verifies cross-boundary search and exact full-document copying.

## Signing rehearsal

Release builds fail clearly without `TEXTR_SIGNING_PROPERTIES`. A disposable, short-lived test keystore outside the repository produced version code 1 and version code 2 APKs. Android build tools verified both signatures and the same certificate. A clean Android 16 installation followed by `adb install -r` retained the selected dark theme and recent history. Package identity, minimum/target SDK, non-debuggable release flags, and packaged notices were inspected.

A clean source archive of commit `9d33ce9`, with no local SDK properties or build outputs, also produced a verified signed APK using the documented external credentials and SDK environment.

The disposable certificate SHA-256 was `8947ee21cba7a639d42527d0b7299707835b5c77cbcc0c031a009a01844fa257`. This is test evidence, **not the production signing identity**. Test APKs are not public release artifacts. Production key custody, final release signing, source/archive/checksum publication, and downloaded-artifact verification belong to deferred [issue #13](https://github.com/utrost/Textr/issues/13).
