#!/usr/bin/env python3
"""Create public, deterministic emulator fixtures; never reads user documents."""
import argparse
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument("destination", type=Path)
args = parser.parse_args()
args.destination.mkdir(parents=True, exist_ok=True)
files = {
    "welcome.md": "# Welcome to Textr\n\nA small, **read-only** viewer for everyday text.\n\n## Reading\n\n- Open a file\n- Find a word\n- Switch to source\n\n| Format | View |\n| --- | --- |\n| Markdown | Preview |\n| JSON | Formatted |\n| Config | Source |\n\n> Your files stay unchanged.\n\n```ini\n[reader]\nwrap = true\n```\n\n[Android documentation](https://developer.android.com)\n",
    "sample.txt": "Textr text fixture\nThe original file stays unchanged.\n",
    "sample.log": "2026-11-12 INFO Textr log fixture\n",
    "sample.conf": "# Textr configuration fixture\nreader.enabled = true\n",
    "sample.cfg": "# Textr configuration fixture\nreader.enabled = true\n",
    "sample.ini": "[reader]\nname = Textr\nwrap = true\n",
    "sample.json": '{"app":"Textr","readOnly":true,"formats":["txt","json","md"],"version":1}\n',
    "sample.yaml": "app: Textr\nreadOnly: true\nformats:\n  - txt\n  - json\n",
    "sample.toml": '[reader]\nname = "Textr"\nwrap = true\n',
    "sample.xml": '<?xml version="1.0"?>\n<reader name="Textr"><wrap>true</wrap></reader>\n',
    "sample.csv": "name,view\nTextr,text\n",
    "UPPER.JSON": '{"app":"Textr uppercase fixture"}\n',
    "extensionless": "Textr extensionless text fixture\n",
    "invalid.json": "{not valid JSON}\n",
    "hostile.md": '# Offline test\n\n<script>alert("blocked")</script>\n\n![remote](https://example.com/pixel)\n\n[Open external](https://example.com/textr-validation)\n',
}
for name, content in files.items():
    (args.destination / name).write_text(content, encoding="utf-8")
limit = 5 * 1024 * 1024
marker = b"\nBoundary end marker\n"
(args.destination / "boundary.txt").write_bytes(b"a" * (limit - len(marker)) + marker)
(args.destination / "oversized.txt").write_bytes(b"a" * (limit + 1))
(args.destination / "binary.txt").write_bytes(bytes(range(256)))
(args.destination / "deep.json").write_text("[" * 200 + "0" + "]" * 200)
(args.destination / "large.md").write_text(("# Heading\n\nText and **emphasis**.\n\n" * 20000))
print(f"Created {len(list(args.destination.iterdir()))} fixtures in {args.destination}")
