//! `uniffi-bindgen` for `storyloop-android`: generates its Kotlin bindings
//! from the built library, e.g.
//! `cargo run -p storyloop-android-bindgen -- generate libstoryloop_android.so --language kotlin --out-dir out`
//! (`android/app/build.gradle.kts` runs it).
#![forbid(unsafe_code)]

fn main() {
    uniffi::uniffi_bindgen_main()
}
