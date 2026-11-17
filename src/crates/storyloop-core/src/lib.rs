//! StoryLoop's engine core: the project document, the commands that edit it,
//! and the engine that owns undo and redo.
//!
//! The document is the truth: a canvas format, a project length, and lanes
//! (timeline rows) holding clips. The UI never edits it directly — it sends
//! one [`Command`] per edit, and each command is one undo step.

#![deny(unsafe_code)]

pub mod command;
pub mod document;
pub mod engine;

pub use command::{Command, EngineError};
pub use document::{Clip, ClipKind, Document, Format, Lane, LaneKind};
pub use engine::Engine;

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn adding_media_makes_a_clip_on_the_main_lane() {
        let mut doc = Document::new(Format::Reel);
        let id = doc
            .apply(Command::AddMedia {
                path: "/sdcard/a.jpg".into(),
                is_video: false,
                start_us: 0,
                duration_us: 5_000_000,
            })
            .unwrap();
        assert_eq!(doc.clips().count(), 1);
        assert!(doc.clips().any(|(lane, c)| lane == LaneKind::Main && c.id == id));
    }

    #[test]
    fn undo_rolls_the_last_command_back() {
        let mut engine = Engine::new(Format::Reel);
        engine
            .apply(Command::AddText {
                content: "hi".into(),
                at_us: 0,
                duration_us: 3_000_000,
            })
            .unwrap();
        assert_eq!(engine.document().clips().count(), 1);
        assert!(engine.undo());
        assert_eq!(engine.document().clips().count(), 0);
        assert!(engine.redo());
        assert_eq!(engine.document().clips().count(), 1);
    }

    #[test]
    fn a_span_past_the_project_length_is_clamped() {
        let mut doc = Document::new(Format::Reel); // 15 s
        let id = doc
            .apply(Command::AddMedia {
                path: "v.mp4".into(),
                is_video: true,
                start_us: 20_000_000,
                duration_us: 5_000_000,
            })
            .unwrap();
        let clip = doc.clip_mut(id).unwrap();
        assert_eq!(clip.start_us, 10_000_000);
    }

    #[test]
    fn text_and_stickers_move_on_the_canvas() {
        let mut doc = Document::new(Format::Reel);
        let id = doc
            .apply(Command::AddSticker {
                glyph: "🔥".into(),
                at_us: 0,
                duration_us: 2_000_000,
            })
            .unwrap();
        doc.apply(Command::MoveClip { id, x: 0.25, y: 0.75 }).unwrap();
        assert!(matches!(
            &doc.clip_mut(id).unwrap().kind,
            ClipKind::Sticker { x, y, .. }
                if (*x - 0.25).abs() < f32::EPSILON && (*y - 0.75).abs() < f32::EPSILON
        ));
    }

    #[test]
    fn moving_media_is_refused() {
        let mut doc = Document::new(Format::Reel);
        let id = doc
            .apply(Command::AddMedia {
                path: "a.jpg".into(),
                is_video: false,
                start_us: 0,
                duration_us: 2_000_000,
            })
            .unwrap();
        assert!(doc.apply(Command::MoveClip { id, x: 0.5, y: 0.5 }).is_err());
    }
}
