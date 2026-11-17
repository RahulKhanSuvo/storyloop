//! The engine: the document plus undo and redo.
//!
//! Undo works on snapshots: the document is tiny (no pixels, just references
//! and numbers), so cloning it per command is cheap and simple.

use crate::command::{Command, EngineError};
use crate::document::{Document, Format};

/// Holds the project and its history; every [`Engine::apply`] is one undo step.
#[derive(Debug, Clone)]
pub struct Engine {
    document: Document,
    undo_stack: Vec<Document>,
    redo_stack: Vec<Document>,
}

impl Engine {
    /// A fresh engine for a new `format` project.
    pub fn new(format: Format) -> Self {
        Engine {
            document: Document::new(format),
            undo_stack: Vec::new(),
            redo_stack: Vec::new(),
        }
    }

    /// Applies `command`; the pre-edit document is kept for undo.
    pub fn apply(&mut self, command: Command) -> Result<u64, EngineError> {
        let snapshot = self.document.clone();
        let created = self.document.apply(command)?;
        self.undo_stack.push(snapshot);
        self.redo_stack.clear();
        Ok(created)
    }

    /// Rolls the last command back; false when there is nothing to undo.
    pub fn undo(&mut self) -> bool {
        match self.undo_stack.pop() {
            Some(previous) => {
                self.redo_stack.push(std::mem::replace(&mut self.document, previous));
                true
            }
            None => false,
        }
    }

    /// Re-applies the last undone command; false when there is nothing to redo.
    pub fn redo(&mut self) -> bool {
        match self.redo_stack.pop() {
            Some(next) => {
                self.undo_stack.push(std::mem::replace(&mut self.document, next));
                true
            }
            None => false,
        }
    }

    /// The current document.
    pub fn document(&self) -> &Document {
        &self.document
    }

    /// Whether undo has something to roll back.
    pub fn can_undo(&self) -> bool {
        !self.undo_stack.is_empty()
    }

    /// Whether redo has something to re-apply.
    pub fn can_redo(&self) -> bool {
        !self.redo_stack.is_empty()
    }
}
