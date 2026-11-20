# Everyday source formats

These formats add approximate source colors and manual **Treat as** entries; they do not add execution, interpolation, compilation or an HTML/JSX preview. Source selection/search and read-only original-byte sharing use the existing reader paths. Highlighting remains limited to 512 Ki characters and 10,000 decorated tokens, with complete escaped source after the limit. Larger files remain plain text within the 5 MiB opening limit.

| Format | Filename recognition | MIME fallback | Decoration |
| --- | --- | --- | --- |
| .env | `.env`, `.env.local`, `.env.example`, any `.env.<variant>`, and `.env` extension | `text/x-env` | Keys, optional export prefix, quoted/unquoted values, comments and escapes; multiline quoted values are approximate |
| .properties | `.properties` extension | `text/x-java-properties`, `text/x-properties`, `application/x-java-properties` | Keys, escaped separators, separators, comments, values, raw escape spelling and continuation lines |
| Dockerfile | `Dockerfile`, `Dockerfile.<variant>`, `<variant>.Dockerfile` | `text/x-dockerfile` | Common instruction names, leading comments/directives, strings and continuation escapes |
| TypeScript | `.ts` | `text/typescript`, `application/typescript`, `text/x-typescript` | JavaScript tokens plus common TypeScript keywords/types |
| TSX | `.tsx` | `text/tsx`, `text/x-tsx` | TypeScript tokens and escaped tag-name decoration |
| TSV | `.tsv` | `text/tab-separated-values`, `text/tsv`, `application/tsv` | Existing CSV table and native accessible-table paths, initially source-first |

Matching is case-insensitive. A recognized extension wins before special filename rules and MIME fallback: `Dockerfile.md` remains Markdown, `.env.json` remains JSON, and `.env.local.txt` remains plain text. Special names win over MIME fallback. Unknown names may use the MIME aliases above. `.ts` recognition does not imply support for MPEG transport streams: existing decoding and binary checks still apply. Properties files retain the chosen source encoding and literal `\uXXXX` spellings; older non-UTF files use the existing explicit encoding recovery flow.

For previously unconfigured TSV input, the table delimiter defaults to Tab. **Table options → Preview** records an explicit delimiter/header choice, including an explicit Comma, and restores it across reload, reopen and recreation. Ordinary `.csv`/`.txt` filename precedence is retained even if a sender reports a TSV MIME type. Old records with a non-comma delimiter, enabled header or explicit Treat as CSV preserve those choices; old default records remain unconfigured. No second parser or delimiter guessing is introduced. Quoting, embedded tabs/newlines, table limits and native source fallback are inherited from CSV.

Colors are a reading aid. `.env` has multiple dialects; no environment expansion or shell behavior is implied. Properties escapes are decorated, not decoded or loaded into a `Properties` map. Docker embedded shell/heredocs and escape directives receive approximate colors; nothing is built. TypeScript/TSX generic angle brackets, regex literals, template interpolation and nested JSX may be approximate; original tags remain escaped text and JavaScript remains disabled.

Syntax references: [Node.js dotenv conventions](https://nodejs.org/api/environment_variables.html#dotenv), [Java Properties syntax](https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/util/Properties.html), [Dockerfile reference](https://docs.docker.com/reference/dockerfile/), [TypeScript JSX](https://www.typescriptlang.org/docs/handbook/jsx.html).
