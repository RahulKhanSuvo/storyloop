# StoryLoop

A template-based short-video creation app for Android: pick a template, replace
its media/text/colors, preview it animated, and export an MP4.

**Structure** — a Rust engine with a Kotlin/Jetpack Compose app, connected by
UniFFI (the same architecture CapCraft uses):

```
storyloop/
├── docs/                                  architecture notes
└── src/                                   the cargo workspace
    ├── Cargo.toml                         workspace: crates/*
    ├── rust-toolchain.toml
    └── crates/
        ├── storyloop-core/                the engine core: document, commands, undo/redo
        └── storyloop-android/             the engine as a native library for Android
            ├── src/                       UniFFI surface (EditorSession, TimelineView)
            ├── bindgen/                   uniffi-bindgen; generates Kotlin bindings
            └── android/                   the Android app (Gradle project)
                ├── settings.gradle.kts
                └── app/                   Compose UI: home, create sheet, editor
```

**How the halves talk** — every edit the UI makes is one command (JSON, written
by kotlinx.serialization) applied to `EditorSession` in Rust; every call returns
a `TimelineView` snapshot the UI renders. One command is one undo step. Time
crosses the bridge as whole microseconds. The Gradle build compiles the Rust
library with cargo-ndk and generates the Kotlin bindings with uniffi-bindgen —
one `./gradlew assembleDebug` builds everything.

**Building** — from `src/crates/storyloop-android/android`:
```
./gradlew assembleDebug
```
Needs: Rust (rustup, with the `aarch64-linux-android` target), cargo-ndk, the
Android SDK + NDK. The engine's UI-facing parts have no heavy native
dependencies yet; media decode/render crates arrive with the rendering milestone.

Engine crates planned next: `storyloop-media` (decode), `storyloop-render`
(compositing), `storyloop-export` (MP4 encode).
