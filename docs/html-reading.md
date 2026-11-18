# HTML Markdown reading view

The candidate implementation for [#68](https://github.com/utrost/Textr/issues/68) and [#69](https://github.com/utrost/Textr/issues/69) adds an offline reading view for saved HTML. It is newer than the immutable `v0.1-alpha` testing release and is not included in that APK.

## Open and read

Open a local `.html`, `.htm` or `.xhtml` file through the system picker, **Open with**, or an HTML attachment shared from another app. HTML opens as **Highlighted** source. Tap the display-mode button and choose **Markdown reading view** (**Markdown-Leseansicht** in German). **Plain text** and **Highlighted** always show the original HTML source. The chosen mode follows the existing saved reading-state rules.

Apps can also send `text/html` or `application/xhtml+xml` text through Android Share. When an app supplies `EXTRA_HTML_TEXT`, Textr uses that explicit HTML instead of its plain-text alternative. Shared text remains session-only and is excluded from recent-file history. Many browsers share only a URL: those shares remain plain text. Textr does not download the page or resolve a website's resources.

The reading view converts the local HTML body to Markdown, then uses the existing restricted Markdown renderer. It retains headings, paragraphs, emphasis, lists, blockquotes, inline/fenced code, safe absolute web/email links and simple rectangular tables. Converted headings appear in **Heading outline**. Find searches the displayed reading view; **Search source…** searches the original HTML.

This is a document reading view, not a browser or automatic article extractor. Page layout/CSS is not reproduced, and navigation/header/footer text in the HTML body may remain. The result depends on the saved document containing the text: script-generated or unloaded website content cannot be recovered.

## Offline and source integrity

Original HTML is never loaded as a WebView page. Scripts, styles, embedded frames/objects, forms, SVG/math, media and hidden/ARIA-hidden subtrees are omitted; images become a localized omission label. CSS visibility rules are not evaluated. Literal text is escaped before Markdown parsing, so Markdown-like text cannot create unexpected links, images or formatting.

Only absolute `http`, `https` and `mailto` links are retained. Relative URLs, original-document fragments, unsafe schemes and malformed links become their visible label. No base URL is inferred, and `<base>` is ignored. Retained links use the existing external-handler action after a user tap. The app still has no network permission, and WebView JavaScript, network/resource loading, file/content access and DOM storage stay disabled.

Conversion never changes the original bytes or replaces the shared file with generated Markdown. Sharing URI-backed documents continues to copy original bytes with read-only grants, including their original encoding/BOM/line endings. Sharing short, session-only HTML text sends its original decoded text with the HTML MIME type. There is no generated-Markdown export in this change.

## Limits and fallback

The source-file limit stays **5 MiB**. HTML conversion accepts at most **256 Ki UTF-16 characters**, **20,000 DOM nodes** and DOM depth **64** (including document/html/body wrappers). Generated Markdown is capped at **512 Ki characters** and then passes the existing Markdown **20,000-node / depth-64** limits. Parsing, traversal and conversion check cancellation.

Simple tables accept up to **100 rows**, **32 columns** and **1,000 cells**. A headerless table gets an empty header, preserving the first data row. Spanning/ragged/nested tables and preformatted blocks within cells fall back to source. Images and interactive controls are deliberately not converted. If no readable output remains, parsing/conversion fails, or a budget is exceeded, the reading option is unavailable and the complete original source remains readable. The input-size limit has a localized explanation; other unavailable previews use the existing fallback notice.

Parser: pinned [jsoup 1.24.1](https://jsoup.org/download), used only with an in-memory reader and no network API. Its [MIT license](../app/src/main/assets/licenses/Jsoup-MIT.txt) and attribution are bundled in About → Open-source licenses.

See [HTML validation evidence](validation-html-reading.md) for test coverage, exact source/APK identities and remaining device/CI limits.
