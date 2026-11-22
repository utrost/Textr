#!/usr/bin/env bash
# Keep the test exit code while collecting diagnostics before the runner stops its emulator.
set -euo pipefail
# Compile before the final UI readiness check, separating build load from the
# first instrumentation test on cold Google APIs images.
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest --stacktrace
if ! .github/scripts/check-test-device.sh; then
  .github/scripts/collect-test-diagnostics.sh || true
  exit 1
fi
test_status=0
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.minimumHeapMiB=128 --stacktrace || test_status=$?
if [ "$test_status" -ne 0 ]; then
  .github/scripts/collect-test-diagnostics.sh || true
fi
exit "$test_status"
