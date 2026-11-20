# Security

Textr is a development-stage application; there is no supported public binary release yet. Security fixes are developed on `main`. Files and providers should be treated as untrusted input.

For a vulnerability, use GitHub’s **Security → Report a vulnerability** on [this repository](https://github.com/utrost/Textr/security/advisories/new) when available. If private reporting is unavailable, open a minimal issue asking for a private reporting channel, without exploit details or sensitive files. No response-time guarantee is offered.

Include the affected commit/build, Android and WebView versions, expected impact, and a minimal synthetic reproducer. Do not upload real documents, access tokens, keystores or signing credentials. Ordinary UI bugs belong in the public issue tracker.

The security boundaries include no app network permission, read-only document access, bounded input/rendering, disabled JavaScript and remote WebView resources, and read-only grants for explicit sharing. External document providers, browsers, receiving apps and Android’s WebView implementation have their own behavior and update lifecycle.
