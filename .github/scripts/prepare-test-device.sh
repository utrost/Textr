#!/usr/bin/env bash
# Disposable, rootable Google APIs emulator only; never changes a physical device.
set -euo pipefail
device_serial="${1:-emulator-5554}"
adb_command="${ANDROID_HOME:?}/platform-tools/adb"
if [[ "$("$adb_command" -s "$device_serial" shell getprop ro.kernel.qemu | tr -d '\r')" != 1 ]]; then
  echo 'Expected a disposable Android emulator.' >&2
  exit 1
fi
"$adb_command" -s "$device_serial" root || true # adbd may close the connection while restarting.
root_ready=false
for attempt in $(seq 1 20); do
  "$adb_command" -s "$device_serial" wait-for-device
  if [[ "$("$adb_command" -s "$device_serial" shell id -u 2>/dev/null | tr -d '\r')" == 0 ]]; then
    root_ready=true
    break
  fi
  sleep 1
done
[[ "$root_ready" == true ]]
# An API 29 guest retained ART's 16 MiB default despite its 512m property.
# Restart Android with both budgets set; instrumentation verifies the runtime.
"$adb_command" -s "$device_serial" shell setprop dalvik.vm.heapgrowthlimit 256m
"$adb_command" -s "$device_serial" shell setprop dalvik.vm.heapsize 512m
"$adb_command" -s "$device_serial" shell stop
"$adb_command" -s "$device_serial" shell setprop sys.boot_completed 0
"$adb_command" -s "$device_serial" shell start
boot_ready=false
for attempt in $(seq 1 120); do
  if [[ "$("$adb_command" -s "$device_serial" shell getprop sys.boot_completed | tr -d '\r')" == 1 ]]; then
    boot_ready=true
    break
  fi
  sleep 1
done
[[ "$boot_ready" == true ]]
for animation_setting in window_animation_scale transition_animation_scale animator_duration_scale; do
  "$adb_command" -s "$device_serial" shell settings put global "$animation_setting" 0
done
"$adb_command" -s "$device_serial" shell settings put system screen_off_timeout 2147483647
"$adb_command" -s "$device_serial" shell getprop dalvik.vm.heapgrowthlimit
"$adb_command" -s "$device_serial" shell getprop dalvik.vm.heapsize
