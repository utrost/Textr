# Additional text formats

Textr opens supported text encodings up to 5 MiB. Format detection uses the filename extension first and the provider's MIME type as a fallback. Extensions are case-insensitive. **Document info → Treat as…** overrides detection and remembers the choice for that document.

## Source formats

| Format | Extensions | Display |
| --- | --- | --- |
| Java | `.java` | Highlighted source |
| JavaScript | `.js`, `.mjs`, `.cjs` | Highlighted source |
| CSS | `.css` | Highlighted source |
| HTML | `.html`, `.htm`, `.xhtml` | Highlighted source |
| Kotlin | `.kt`, `.kts` | Highlighted source |
| Python | `.py`, `.pyw` | Highlighted source |
| Shell | `.sh`, `.bash` | Highlighted source |
| SQL | `.sql` | Highlighted source |
| Diff / patch | `.diff`, `.patch` | Headers, additions, and deletions distinguished |
| CSV | `.csv` | Plain text by default; optional bounded table preview with explicit delimiter/header settings |
| iCalendar | `.ics`, `.ical` | Event preview when supported; highlighted source and plain text available |

The source lexers decorate common keywords, comments, strings, and numbers. CSS also distinguishes selectors and properties; iCalendar distinguishes property headers, parameters, and values. These are lightweight lexers, not language parsers: embedded HTML languages, JavaScript regex literals/template interpolation, nested comments, Python f-string expressions, shell heredocs, and dialect-specific syntax may receive approximate colors. Files are never executed or validated as programs. HTML is displayed as escaped source with no browser preview.

Existing highlighting budgets apply: at most 512 Ki characters and 10,000 decorated tokens. Text beyond the token budget remains visible without colors; larger files use plain text. Original whitespace, line endings, Unicode, and characters remain intact. Line numbering, Go to line, search, selection, and copying work on source views; sharing and Open with preserve original bytes.

## iCalendar event preview

The preview summarizes `VEVENT` components in an iCalendar 2.0 `VCALENDAR`. It shows event titles, start/end values, location, description, status, organizer, duration, URL text, and recurrence metadata when supplied. Folded lines and escaped text are decoded only for the preview. Multiple events appear in source order.

- All-day dates are explicitly labeled. End dates/times are exclusive, as specified by iCalendar; no inclusive end date is invented.
- UTC timestamps are labeled UTC. `TZID` values are displayed as supplied, including custom time-zone identifiers. Floating times explicitly say that no time zone was supplied. No time-zone conversion is performed, and `VTIMEZONE` definitions are not interpreted.
- Recurrence rules, additional dates, exclusions, and recurrence-instance identifiers are shown without expanding them into occurrences. Cancellation/status and recurrence-range information remain visible. Durations and recurrence date lists are displayed as supplied, not calculated or validated.
- Alarm fields are excluded from event details. Tasks, journals, free/busy information, attachments, and extension properties remain available in source; the preview explicitly says it is an event summary.
- URLs are inert text. No resources are fetched, JavaScript remains disabled, and the app requests no calendar permission. Viewing does not import events or schedule alarms.

The preview is bounded to 512 Ki input characters, 200 events, 10,000 content lines (including component boundaries), 16 nesting levels, 16,384 characters per unfolded line, and 2 Mi output characters. Unsupported structure, malformed displayed dates, ambiguous duplicate summary/date fields, or exceeded limits cause the whole preview to fall back to complete source with an explanation. A task-only calendar also falls back. This summary is not a full iCalendar validator; leap-second timestamps and older calendar versions are outside preview support.

Calendar semantics follow [RFC 5545](https://www.rfc-editor.org/rfc/rfc5545.html), with parameter escaping from [RFC 6868](https://www.rfc-editor.org/rfc/rfc6868.html). No calendar library, network access, or runtime script engine is added.

## Android integration

The existing `text/*` association covers text MIME types including `text/calendar`, `text/css`, and `text/javascript`. Additional VIEW/SEND aliases cover providers reporting JavaScript, Java source, XHTML, Python, shell, or SQL under `application/*`. Sending apps and MIME metadata still determine whether Android offers Textr; the picker remains the fallback. Java bytecode and archives are not source formats.

Sharing uses a format-appropriate MIME type such as `text/calendar` or `text/javascript`. **Open with…** still hands another app a read-only temporary copy. The receiving app decides what to do with it, including offering its own calendar-import workflow.

## CSV table preview

See [reading extras](reading-extras.md) for the CSV subset, limits, delimiter/header choices, source fallback and selection behavior. Cells are escaped text; formulas, links and HTML are never executed.
