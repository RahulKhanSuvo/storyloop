//! The project document: the canvas format, the timeline's lanes, and their clips.
//!
//! Time is whole microseconds everywhere, never floating-point seconds.

use serde::{Deserialize, Serialize};

/// A canvas format the reel is made for; each maps to a width:height ratio.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum Format {
    /// Vertical 9:16, the standard short-video canvas.
    Reel,
    /// Vertical 4:5, social feed posts.
    Portrait,
    /// Square 1:1.
    Square,
    /// Horizontal 16:9.
    Landscape,
}

impl Format {
    /// Width divided by height, the ratio the preview canvas and the export use.
    pub fn ratio(self) -> f32 {
        match self {
            Format::Reel => 9.0 / 16.0,
            Format::Portrait => 4.0 / 5.0,
            Format::Square => 1.0,
            Format::Landscape => 16.0 / 9.0,
        }
    }

    /// The length a new project starts with: 15 s, like the reference editors.
    pub fn default_duration_us(self) -> i64 {
        15_000_000
    }

    /// The format from its snake_case name.
    pub fn from_name(name: &str) -> Option<Self> {
        match name {
            "reel" => Some(Format::Reel),
            "portrait" => Some(Format::Portrait),
            "square" => Some(Format::Square),
            "landscape" => Some(Format::Landscape),
            _ => None,
        }
    }

    /// The format's snake_case name.
    pub fn name(self) -> &'static str {
        match self {
            Format::Reel => "reel",
            Format::Portrait => "portrait",
            Format::Square => "square",
            Format::Landscape => "landscape",
        }
    }
}

/// One timeline row; its kind decides what clips it holds and where it shows.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum LaneKind {
    /// Music and narration.
    Audio,
    /// Emoji stickers and labels.
    Sticker,
    /// Text overlays.
    Text,
    /// Freehand drawing over the frame.
    Draw,
    /// The main visual track: photos and videos, sequenced back to back.
    Main,
}

/// Every lane kind, in the order rows show on the timeline, top to bottom.
pub const LANE_ORDER: [LaneKind; 5] = [
    LaneKind::Audio,
    LaneKind::Sticker,
    LaneKind::Text,
    LaneKind::Draw,
    LaneKind::Main,
];

/// A lane: one row of clips that all belong together.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Lane {
    pub kind: LaneKind,
    pub clips: Vec<Clip>,
}

impl Lane {
    fn new(kind: LaneKind) -> Self {
        Lane { kind, clips: Vec::new() }
    }

    /// The clip holding `id`, if it is on this lane.
    pub fn clip_mut(&mut self, id: u64) -> Option<&mut Clip> {
        self.clips.iter_mut().find(|c| c.id == id)
    }
}

/// One clip: a span of time on one lane carrying its own content.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Clip {
    pub id: u64,
    /// Where the clip begins on the timeline, in microseconds.
    pub start_us: i64,
    /// How long the clip runs, in microseconds; always positive.
    pub duration_us: i64,
    #[serde(flatten)]
    pub kind: ClipKind,
}

impl Clip {
    /// Where the clip ends: `start_us + duration_us`.
    pub fn end_us(&self) -> i64 {
        self.start_us + self.duration_us
    }
}

/// What a clip shows or plays.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "type", rename_all = "snake_case")]
pub enum ClipKind {
    /// A photo or video from the device gallery.
    Media { path: String, is_video: bool },
    /// A text overlay; `x`/`y` are the normalized center (0..1) of the canvas,
    /// `color` a hex string like "#ffffff", `size` the font size in sp.
    Text { content: String, color: String, size: f32, x: f32, y: f32 },
    /// An emoji sticker; `x`/`y` are the normalized center, `scale` multiplies
    /// the base sticker size.
    Sticker { glyph: String, x: f32, y: f32, scale: f32 },
    /// Freehand drawing over the frame; stroke paths are opaque for now.
    Draw { strokes: u32 },
    /// Music or narration from the device.
    Audio { path: String },
}

/// The whole project: the canvas format, the project length, and the lanes.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Document {
    pub format: Format,
    /// The project's length on the timeline, in microseconds.
    pub duration_us: i64,
    pub lanes: Vec<Lane>,
    next_id: u64,
}

impl Document {
    /// A fresh document for `format`, with every lane present and empty.
    pub fn new(format: Format) -> Self {
        Document {
            format,
            duration_us: format.default_duration_us(),
            lanes: LANE_ORDER.iter().map(|k| Lane::new(*k)).collect(),
            next_id: 1,
        }
    }

    /// The id the next new clip gets.
    fn take_id(&mut self) -> u64 {
        let id = self.next_id;
        self.next_id += 1;
        id
    }

    /// The clip with `id`, wherever it sits, for editing in place.
    pub fn clip_mut(&mut self, id: u64) -> Option<&mut Clip> {
        self.lanes.iter_mut().find_map(|lane| lane.clip_mut(id))
    }

    /// The lane that holds clips of `kind`.
    fn lane_mut(&mut self, kind: LaneKind) -> &mut Lane {
        self.lanes.iter_mut().find(|l| l.kind == kind).expect("every lane kind exists")
    }

    /// Every clip with the lane kind it sits on, lane order first.
    pub fn clips(&self) -> impl Iterator<Item = (LaneKind, &Clip)> {
        self.lanes.iter().flat_map(|l| l.clips.iter().map(move |c| (l.kind, c)))
    }

    /// Applies `command`, returning the id of the clip it created (when it made one).
    pub fn apply(&mut self, command: Command) -> Result<u64, EngineError> {
        match command {
            Command::SetDuration { duration_us } => {
                if !(1_000_000..=120_000_000).contains(&duration_us) {
                    return Err(EngineError::Invalid(format!("duration {duration_us}us")));
                }
                self.duration_us = duration_us;
                Ok(0)
            }
            Command::AddMedia { path, is_video, start_us, duration_us } => {
                let (start_us, duration_us) = clamped_span(start_us, duration_us, self.duration_us)?;
                let id = self.take_id();
                self.lane_mut(LaneKind::Main).clips.push(Clip {
                    id,
                    start_us,
                    duration_us,
                    kind: ClipKind::Media { path, is_video },
                });
                Ok(id)
            }
            Command::AddText { content, at_us, duration_us } => {
                let (start_us, duration_us) = clamped_span(at_us, duration_us, self.duration_us)?;
                let id = self.take_id();
                self.lane_mut(LaneKind::Text).clips.push(Clip {
                    id,
                    start_us,
                    duration_us,
                    kind: ClipKind::Text { content, color: "#ffffff".into(), size: 20.0, x: 0.5, y: 0.5 },
                });
                Ok(id)
            }
            Command::UpdateText { id, content } => {
                let clip = self.clip_mut(id).ok_or(EngineError::UnknownClip(id))?;
                match &mut clip.kind {
                    ClipKind::Text { content: old, .. } => *old = content,
                    _ => return Err(EngineError::Invalid("the clip is not text".into())),
                }
                Ok(id)
            }
            Command::AddSticker { glyph, at_us, duration_us } => {
                let (start_us, duration_us) = clamped_span(at_us, duration_us, self.duration_us)?;
                let id = self.take_id();
                self.lane_mut(LaneKind::Sticker).clips.push(Clip {
                    id,
                    start_us,
                    duration_us,
                    kind: ClipKind::Sticker { glyph, x: 0.5, y: 0.5, scale: 1.0 },
                });
                Ok(id)
            }
            Command::MoveClip { id, x, y } => {
                if !(0.0..=1.0).contains(&x) || !(0.0..=1.0).contains(&y) {
                    return Err(EngineError::Invalid(format!("position {x},{y}")));
                }
                let clip = self.clip_mut(id).ok_or(EngineError::UnknownClip(id))?;
                match &mut clip.kind {
                    ClipKind::Text { x: px, y: py, .. } | ClipKind::Sticker { x: px, y: py, .. } => {
                        *px = x;
                        *py = y;
                    }
                    _ => {
                        return Err(EngineError::Invalid(
                            "only text and stickers move on the canvas".into(),
                        ))
                    }
                }
                Ok(id)
            }
            Command::TrimClip { id, start_us, duration_us } => {
                if duration_us <= 0 || start_us < 0 {
                    return Err(EngineError::Invalid(format!("span {start_us}us + {duration_us}us")));
                }
                let clip = self.clip_mut(id).ok_or(EngineError::UnknownClip(id))?;
                clip.start_us = start_us;
                clip.duration_us = duration_us;
                Ok(id)
            }
            Command::DeleteClip { id } => {
                let mut removed = false;
                for lane in &mut self.lanes {
                    let before = lane.clips.len();
                    lane.clips.retain(|c| c.id != id);
                    removed |= lane.clips.len() != before;
                }
                if !removed {
                    return Err(EngineError::UnknownClip(id));
                }
                Ok(id)
            }
        }
    }
}

use crate::command::{Command, EngineError};

/// Clamps a requested span into the project, so a new clip never hangs past its end.
fn clamped_span(start_us: i64, duration_us: i64, project_us: i64) -> Result<(i64, i64), EngineError> {
    if duration_us <= 0 {
        return Err(EngineError::Invalid(format!("duration {duration_us}us")));
    }
    let latest = (project_us - duration_us).max(0);
    Ok((start_us.clamp(0, latest), duration_us))
}
