#!/usr/bin/env bash
# Keep the test exit code while collecting diagnostics before the runner stops its emulator.
set -euo pipefail
test_status=0
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.minimumHeapMiB=128 --stacktrace || test_status=$?
if [ "$test_status" -ne 0 ]; then
  .github/scripts/collect-test-diagnostics.sh || true
fi
exit "$test_status"
