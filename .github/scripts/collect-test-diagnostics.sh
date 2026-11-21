#!/usr/bin/env bash
# Fixed-name diagnostics from synthetic instrumentation fixtures; never copy app data wholesale.
set -euo pipefail
device_serial="${1:-emulator-5554}"
adb_command="${ANDROID_HOME:?}/platform-tools/adb"
destination="app/build/outputs/device-diagnostics"
mkdir -p "$destination"
for prefix in reader-test-failure reader-polish-timeout foldable-close extras-control selection-node-missing; do
  for extension in xml png; do
    name="$prefix.$extension"
    # exec-out can return zero while forwarding a remote cat error on stdout.
    # Check existence through shell's exit-status protocol before copying bytes.
    if "$adb_command" -s "$device_serial" shell run-as de.utrost.textr test -f "cache/$name" 2>/dev/null; then
      if ! "$adb_command" -s "$device_serial" exec-out run-as de.utrost.textr cat "cache/$name" > "$destination/$name" 2>/dev/null; then
        rm -f "$destination/$name"
      fi
    else
      rm -f "$destination/$name"
    fi
  done
done
