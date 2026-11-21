#!/usr/bin/env python3
"""Independently apply Kotlin-generated exact patches with GNU patch --binary."""
from pathlib import Path
import subprocess
import tempfile

fixtures = Path('app/build/reports/unified-diff-fixtures')
cases = sorted(fixtures.glob('case-*'))
assert cases, 'Run :app:testDebugUnitTest first'
for case in cases:
    before = (case / 'before').read_bytes()
    after = (case / 'after').read_bytes()
    patch = (case / 'changes.patch').read_bytes()
    with tempfile.TemporaryDirectory(prefix='textr-patch-') as directory:
        target = Path(directory) / 'original'
        target.write_bytes(before)
        if patch:
            result = subprocess.run(['patch', '--binary', '--batch', '--fuzz=0', '-p1', '-i', str((case / 'changes.patch').resolve())], cwd=directory, capture_output=True)
            assert result.returncode == 0, (case.name, result.stdout.decode(), result.stderr.decode())
        assert target.read_bytes() == after, f'{case.name}: patched bytes differ'
print(f'{len(cases)} exact unified diffs independently applied successfully')
