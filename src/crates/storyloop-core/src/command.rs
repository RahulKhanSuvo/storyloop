//! Commands: every edit the UI can ask for, applied one at a time.
//!
//! Commands cross the UniFFI bridge as JSON (Kotlin's kotlinx.serialization
//! writes them; serde here reads them), tagged by `"type"`.

use serde::{Deserialize, Serialize};

/// What went wrong when a command was applied.
#[derive(Debug, Clone, PartialEq, thiserror::Error)]
pub enum EngineError {
    /// The command names a clip that does not exist.
    #[error("no clip has id {0}")]
    UnknownClip(u64),
    /// The command's numbers are out of range or it does not fit its clip.
    #[error("{0}")]
    Invalid(String),
}

/// One edit. Applying it is one undo step.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "type", rename_all = "snake_case")]
pub enum Command {
    /// Change the project's length on the timeline (1 s to 2 min).
    SetDuration { duration_us: i64 },
    /// Add a photo or video to the main track.
    AddMedia { path: String, is_video: bool, start_us: i64, duration_us: i64 },
    /// Add music or narration to the audio lane.
    AddAudio { path: String, name: String, at_us: i64, duration_us: i64 },
    /// Move a main-track clip to another place in the sequence; the track
    /// re-times so the clips stay back to back.
    ReorderMain { id: u64, index: u32 },
    /// Add a text overlay.
    AddText { content: String, at_us: i64, duration_us: i64 },
    /// Change what a text clip says.
    UpdateText { id: u64, content: String },
    /// Add an emoji sticker.
    AddSticker { glyph: String, at_us: i64, duration_us: i64 },
    /// Move a text or sticker clip on the canvas; positions are normalized (0..1).
    MoveClip { id: u64, x: f32, y: f32 },
    /// Move and resize a clip in time.
    TrimClip { id: u64, start_us: i64, duration_us: i64 },
    /// Remove a clip.
    DeleteClip { id: u64 },
}
