# Engine synchronization

The Android engine is synchronized with [storytold/gridcraft](https://github.com/storytold/gridcraft)
at [`f6b0cec67e328f2f6352768b5cb2feb7e562c5db`](https://github.com/storytold/gridcraft/commit/f6b0cec67e328f2f6352768b5cb2feb7e562c5db)
(2026-10-26), engine version **0.12.1**. The previous Android engine version was 0.10.0.

Imported components: core, formula, functions, model, calculation, number formatting, XLSX,
charts, PDF, and engine. Desktop frontends, desktop updater and desktop dependencies are not
part of the Android workspace. The lock file keeps Android's dependencies and adds Chrono,
which the upstream core now uses for date conversion. Rust 1.95 or newer is required; Android's
existing pinned Rust 1.99.0 toolchain is retained.

The Android repository starts with an imported snapshot rather than shared Git ancestry.
The integration uses upstream 0.10.0 snapshot `912b817abe7bb81d7e6c3079953fbf478431bee5` as
the three-way baseline. Android additions are preserved, including shape galleries and drawing,
picture cropping and effects, chart elements and 3-D charts, cell reading order, and measured
column widths. Upstream's active-sheet accessor and shared image bytes are adapted at the
UniFFI boundary. New upstream tests initialize the Android model's additional fields.

Conflict resolutions retain both quoted-list escaping and legacy literal validation lists,
Android's flip/rotation state, the upstream decoded-picture cache with Android crop geometry,
and upstream chart normalization alongside Android chart families. The upstream test that
rejects all flips is adapted to verify supported flip state and rejection of invalid directions.

Validation:

```sh
cargo fmt --all -- --check
cargo test --workspace --exclude gridcraft-android-bindgen --locked
cd apps/gridcraft-android/android
./gradlew assembleDebug testDebugUnitTest
```

On Windows, use a writable `TEMP`/`TMP` directory when a restricted test environment cannot
rename files in its system temporary directory. To use another installed NDK locally, pass
`-Pgridcraft.ndkVersion=<version>`; the default and CI remain pinned to NDK 29.0.14206865.
