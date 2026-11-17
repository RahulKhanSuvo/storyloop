# AGENTS.md — StoryLoop

Working playbook for AI agents (and humans) on this project.

## What this is

**StoryLoop** — a template-based short-video app for Android. Pick a format,
replace its media/text/colors in an editor whose timeline is real, preview it
animated, export an MP4.

**Architecture**: a Rust engine owns the project document; a Kotlin/Jetpack
Compose app renders it. They talk over UniFFI (modeled on CapCraft):

- `src/` — the cargo workspace
  - `crates/storyloop-core` — document, commands, undo/redo (pure, tested)
  - `crates/storyloop-android` — UniFFI surface (`EditorSession`, `TimelineView`)
    - `bindgen/` — uniffi-bindgen (the Gradle build runs it)
    - `android/` — the Gradle project (the app lives INSIDE the crate)
- `docs/architecture.md` — the rules (commands as JSON, snapshots, µs time)

Milestones: editor UI ✅ → rendering (`storyloop-media` decode,
`storyloop-render` composite, `storyloop-export` MP4) → templates.

## Verified commands

```bash
export PATH="$HOME/.cargo/bin:$PATH"          # needed in every fresh shell
cd src && cargo test                          # engine tests (host)

cd src/crates/storyloop-android/android
./gradlew assembleDebug testDebugUnitTest     # compiles Rust via cargo-ndk,
                                              # regenerates bindings, builds APK
./gradlew :app:installDebug                   # install on device
```

First Gradle build compiles the Rust library — expect several minutes and
high memory.

## Environment

- Android SDK `/home/rahul/android-sdk` (NDK 27.1.12297006); physical device
  Android 14, serial `8PP7UORCS8RK6LJB`
- Gradle toolchain pinned to 17 (JDK 21 system-wide); Compose BOM 2026.03.01
- `storyloop.abis` gradle property defaults to `arm64-v8a`
- Machine has 7 GB RAM: never run Gradle and big cargo builds at once;
  `./gradlew --stop` after long sessions; don't run `./gradlew clean`
- If rustup/cargo-ndk are missing: `rustup default stable`,
  `rustup target add aarch64-linux-android`,
  `cargo install cargo-ndk --version 4.1.2 --locked`

## Conventions

- 2-space indent everywhere (Kotlin and Rust)
- Time is integer **microseconds** at every boundary — never float seconds
- Every edit is one `Command` (JSON over the bridge) = one undo step; every
  engine call returns a full `TimelineView` snapshot; the UI renders
  snapshots and never mutates project state itself
- When adding a command: serde variant (snake_case) ↔
  `@SerialName` in `Commands.kt` must match exactly
- MediaStore: **never `LIMIT` in sortOrder** (some OEMs reject it); cap in
  Kotlin instead
- Versions only in `gradle/libs.versions.toml`; no hardcoded versions
- No LICENSE file, no license headers (owner request)
- Material icon names: verify against the icons-extended artifact before use
- Strings: every `R.string.x` must exist in `strings.xml` (cross-check
  before building); conflict-marker check too — the owner's git client
  auto-commits and pulls, so `git grep "<<<<<<<"` first

## Device testing

```bash
adb exec-out screencap -p > shot.png     # wake screen first if off:
adb shell input keyevent KEYCODE_WAKEUP
android layout                           # UI tree as JSON
```

- Cold start needs ~6 s sleep before first tap
- Create Reels button sits at `540 954`; system gesture zone is near
  y≥2352 — scroll at y≈2150
- Path to the editor: Create Reels → format card (Reel at x≈64) → editor
