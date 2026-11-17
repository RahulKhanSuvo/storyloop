package com.storyloop.app.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.storyloop.app.core.EditorCommand
import com.storyloop.app.core.EditorTimeline
import com.storyloop.app.core.Engine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The editor's state: the engine's timeline plus which clip is selected. */
data class EditorUiState(
  val timeline: EditorTimeline,
  val selectedClipId: Long? = null,
)

/**
 * Holds the engine session; every edit is one command applied on a worker
 * thread, and the returned snapshot becomes the UI's state.
 */
class EditorViewModel(format: String) : ViewModel() {

  private val engine = Engine(format)
  private val _uiState = MutableStateFlow(EditorUiState(engine.timeline()))
  val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

  /** Applies one edit on a worker thread; the engine owns undo. */
  fun apply(command: EditorCommand) {
    viewModelScope.launch(Dispatchers.IO) {
      runCatching { engine.apply(command) }.onSuccess { timeline ->
        _uiState.value = EditorUiState(timeline)
      }
    }
  }

  /** Undoes the last edit; a no-op when the engine has nothing to undo. */
  fun undo() {
    viewModelScope.launch(Dispatchers.IO) {
      engine.undo()?.let { timeline -> _uiState.value = EditorUiState(timeline) }
    }
  }

  /** Re-applies the last undone edit; a no-op when there is nothing to redo. */
  fun redo() {
    viewModelScope.launch(Dispatchers.IO) {
      engine.redo()?.let { timeline -> _uiState.value = EditorUiState(timeline) }
    }
  }

  /** Selects a clip on the timeline (or clears the selection). */
  fun select(clipId: Long?) {
    _uiState.value = _uiState.value.copy(selectedClipId = clipId)
  }
}
