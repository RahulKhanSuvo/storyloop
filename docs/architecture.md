# Architecture

StoryLoop splits cleanly in two: a **Rust engine** that owns the project and a
**Kotlin/Compose app** that shows it. Modeled on CapCraft's structure.

```
┌────────────────── Kotlin / Compose (the app) ──────────────────┐
│  EditorScreen · Timeline · Dock · panels (Media/Text/Stickers) │
│  EditorViewModel — holds state, sends commands                 │
│       │  every edit = one command (JSON) → Rust, one undo step │
│       ▼                                                        │
│  ── UniFFI bridge (generated Kotlin bindings) ──               │
│       ▼                                                        │
└────────────────── Rust engine (libstoryloop_android.so) ───────┘
   crates:  storyloop-core (document, commands, undo/redo)
            storyloop-android (UniFFI session surface)
            later: storyloop-media · storyloop-render · storyloop-export
```

## The rules

1. **Rust owns the document.** `storyloop-core` holds the timeline (lanes and
   clips), validates every edit, and keeps the undo/redo history. The UI never
   mutates project state directly.
2. **Every edit is a command.** `AddText`, `AddMedia`, `AddSticker`, `MoveClip`,
   `TrimClip`, `DeleteClip`, `SetDuration`, `UpdateText` — one command per user
   action, so one command is one undo step. Commands cross as JSON strings
   (kotlinx.serialization writes, serde reads, `"type"`-tagged enums match).
3. **Every call returns a snapshot.** `EditorSession.apply/undo/redo` return a
   `TimelineView` (format, duration, all clips as `ClipInfo`, undo/redo flags).
   The Compose UI is a pure function of that snapshot.
4. **Time is integer microseconds at the boundary.** Never floats.
5. **Threading.** The session lock is held only while a call reads or edits the
   document. The app calls from background threads, never the main one.

## How the build ties together

The Android Gradle project lives at `src/crates/storyloop-android/android`,
inside the cargo workspace. `app/build.gradle.kts` registers two tasks per
variant:

- `cargoBuild<Variant>` — runs `cargo ndk -t arm64-v8a --platform 24 -o <out>
  build --release -p storyloop-android --lib`, producing
  `libstoryloop_android.so` into the variant's jniLibs.
- `uniffiBindgen<Variant>` — runs `uniffi-bindgen generate` (library mode)
  against that `.so`, generating Kotlin bindings into the variant's source set
  (package `com.storyloop.app.core`).

`System.loadLibrary("storyloop_android")` loads the library; the generated
`EditorSession` object is the only type the UI needs.
