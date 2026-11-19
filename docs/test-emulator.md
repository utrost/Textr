# Disposable emulator test runtime

CI uses rootable Google APIs x86_64 emulators on API 29 and 36. The AVD configuration explicitly requests 2048 MiB RAM and a 256 MiB VM heap. The pinned emulator-runner action writes `hw.heapSize` for its heap input; the emulator's VM setting is `vm.heapSize`, so the pre-launch Python script writes the actual keys.

Runtime properties alone are insufficient evidence. During the 19 November roadmap regression run, API 29 reported a `512m` heap property but its system process crashed with a **16 MiB growth limit**. The guest was restarted by Android after that failure. The bootstrap now sets both ART properties before restarting Android services: `256m` growth limit and `512m` maximum. Instrumentation records the effective app runtime and CI requires at least 128 MiB. A local bootstrapped run reports **256 MiB effective app heap** and passes the unchanged 5 MiB last-byte-readability check. Remaining regressions run separately.

`.github/scripts/prepare-test-device.sh` accepts an emulator serial, checks `ro.kernel.qemu`, requires a rootable image, restarts Android and waits for boot, then disables animations as CI already does. Run it only on disposable test emulators. It does not change the application manifest, request `largeHeap`, reduce the source limit, change assertions or extend deadlines. Google Play images/physical devices are outside this bootstrap's supported targets.

Example:

```sh
ANDROID_HOME=/path/to/test-sdk .github/scripts/prepare-test-device.sh emulator-5554
ANDROID_HOME=/path/to/test-sdk ./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.minimumHeapMiB=128
```

The scripts contain no signing credentials and publish no artifacts.

The emulator executable is pinned to **36.4.10 / build 15004761**, matching the local API 29/36 validation runtime (the Google archive URL was verified available). Hosted build 37.2.13 exited during initial API 36 startup before instrumentation, with the emulator port disappearing; its root cause was not established. Pinning removes that runtime drift while the hosted rerun verifies the same baseline. The bootstrap also resets window size, density and font scale to avoid stale large-text settings after a guest crash.
