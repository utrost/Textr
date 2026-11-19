# Roadmap implementation validation

Implementation started 19 November 2026, with an eight-hour work window. Features are delivered in tested batches; the original private alpha release is unchanged.

## Reading profiles and typography (#76, #77)

- Debug build, unit tests and lint passed for the first implementation.
- Targeted connected run: ReadingProfileDeviceTest and ReleasePolishTest, **13/13 on API 29 and 13/13 on API 36**. This covers profile isolation, switching, rotation, repository/open-session reconstruction, reset, shared-text privacy, legacy metadata, bounds and bookmark cleanup, plus existing cancellation, offline/source and 200% compact/wide dialog checks.
- Renderer test checks prose font/spacing, monospace code, bounded CSS values and unchanged source text.
- English and German controls/help are provided. Human speech-quality acceptance with TalkBack and physical-device review remain part of the existing beta acceptance issue #56.

## CI emulator baseline

The pre-existing API 29 hosted failure included an explicit allocation failure at a 16 MiB heap and subsequent large-file timeouts. The emulator action's pinned implementation sets `hw.heapSize` for its heap input, while the emulator uses `vm.heapSize`. The pre-launch configuration script sets 2048 MiB RAM and a 256 MiB VM heap using the actual AVD keys. Device jobs log the effective runtime heap properties. Application heap flags, source limits, assertions and deadlines are unchanged.

The native CSV 200% dialog test now waits for its asynchronously prepared window to attach and lay out before checking the existing full visibility/text-fit assertions.
