#!/usr/bin/env bash
# Assert native emulator chrome is usable; never dismiss or hide runtime errors.
set -euo pipefail
device_serial="${1:-emulator-5554}"
adb_command="${ANDROID_HOME:?}/platform-tools/adb"
[[ "$("$adb_command" -s "$device_serial" shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]]
"$adb_command" -s "$device_serial" shell input keyevent KEYCODE_WAKEUP
"$adb_command" -s "$device_serial" shell wm dismiss-keyguard
"$adb_command" -s "$device_serial" shell am start -W -a android.intent.action.MAIN -c android.intent.category.HOME
"$adb_command" -s "$device_serial" shell cmd statusbar expand-notifications
"$adb_command" -s "$device_serial" shell cmd statusbar collapse
python3 - "$adb_command" "$device_serial" <<'PY'
import subprocess
import sys
from pathlib import Path
import time
import xml.etree.ElementTree as ET
adb, serial = sys.argv[1:]
command = [adb, '-s', serial]
path = '/data/local/tmp/textr-device-ready.xml'
def refuse(message, hierarchy=b''):
    destination = Path('app/build/outputs/device-diagnostics')
    destination.mkdir(parents=True, exist_ok=True)
    if hierarchy:
        (destination / 'emulator-preflight.xml').write_bytes(hierarchy)
    screenshot = subprocess.run(command + ['exec-out', 'screencap', '-p'], capture_output=True, timeout=10)
    if screenshot.returncode == 0 and screenshot.stdout.startswith(b'\x89PNG'):
        (destination / 'emulator-preflight.png').write_bytes(screenshot.stdout)
    raise SystemExit(message)

deadline = time.monotonic() + 30
while time.monotonic() < deadline:
    subprocess.run(command + ['shell', 'rm', '-f', path], check=True, capture_output=True, timeout=10)
    # uiautomator can exit zero with a null root while font/layout changes settle.
    # Require a fresh parsed hierarchy, never a file left by an earlier attempt.
    subprocess.run(command + ['shell', 'uiautomator', 'dump', path], capture_output=True, timeout=15)
    result = subprocess.run(command + ['exec-out', 'cat', path], capture_output=True, timeout=10)
    try:
        nodes = list(ET.fromstring(result.stdout).iter('node')) if result.returncode == 0 else []
    except ET.ParseError:
        nodes = []
    if any(node.get('resource-id') in {'android:id/aerr_close', 'android:id/aerr_wait'} for node in nodes):
        refuse('Emulator runtime error dialog is present; refusing to start application tests', result.stdout)
    if any('launcher' in node.get('package', '').lower() for node in nodes):
        print('Fresh native launcher/System UI readiness verified')
        break
    time.sleep(1)
else:
    refuse('Emulator launcher is not exposed; native UI is not ready')
PY
