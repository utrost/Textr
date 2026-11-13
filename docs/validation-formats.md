# Additional-format validation

Issues [#36](https://github.com/utrost/Textr/issues/36), [#37](https://github.com/utrost/Textr/issues/37), [#38](https://github.com/utrost/Textr/issues/38), and [#39](https://github.com/utrost/Textr/issues/39) add source highlighting and bounded iCalendar event summaries. [Format behavior and limits](formats.md) define the supported subset. Public release remains deferred.

## Automated coverage

The suite contains 30 JVM tests and 55 Android instrumentation cases. Ten new JVM cases cover:

- Case-insensitive extension detection, MIME aliases, filename precedence, and JavaScript extensions.
- Exact source preservation across comments, multiline strings, Unicode, CR/LF/CRLF, malformed input, and hostile HTML.
- Language-specific tokens, CSS selectors/properties, diff additions/deletions, and decoration/input budgets.
- Calendar line unfolding, escaped text and parameters, multiple events, nested alarms, recurrence rules/exceptions/ranges, and explicit date/time-zone labels.
- Invalid dates, ambiguous duplicate fields, unsupported versions/components, input/event/property/depth/line/output limits, and cancellation.

Three new device cases exercise twenty implicit VIEW/SEND openings across all ten added format families, external-type association aliases, source-only HTML with JavaScript/network/file/content access disabled, format-appropriate outgoing MIME types, exact copied bytes and read-only grants, scrolling to the iCalendar format override, preview search, source switching, and activity recreation. Existing full-suite cases retain cross-APK handoff, native clipboard, accessibility, lifecycle, large-document, and privacy coverage.

## Local evidence

All 30 JVM cases and lint pass. The three additional-format Android 15 cases pass together (63.610 seconds) at 200% system fonts and 360 dp width; this validates the expanded native format list as well as the new reading flows. JavaScript and calendar screenshots were captured and visually inspected at normal font size/density. The final parser change normalizing an omitted `VALUE` parameter to `DATE-TIME` has a passing JVM regression.

The implementation PR runs all 55 instrumentation cases independently on Android 10/API 29 and Android 16/API 36, plus build, unit tests, and lint. Final PR checks and issue completion comments identify the tested source revision.

## Limits

Highlighting is deliberately approximate for advanced language syntax. The calendar summary does not expand recurrence, interpret custom time-zone definitions, convert times, validate every calendar property, or import events. Unsupported or over-budget previews retain complete source. Physical-device testing and human evaluation of screen-reader speech are not claimed. No production key, release tag, or public APK release is created.
