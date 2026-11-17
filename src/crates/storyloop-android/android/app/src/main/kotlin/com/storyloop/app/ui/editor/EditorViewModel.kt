package com.storyloop.app.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.storyloop.app.StartMedia
import com.storyloop.app.core.EditorCommand
import com.storyloop.app.core.EditorTimeline
import com.storyloop.app.core.Engine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
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
 * Holds the engine session. Every request funnels through one FIFO channel
 * into a single worker coroutine, so edits apply in exactly the order they
 * were asked for (a batch of picked media lands in pick order) and nothing
 * races the engine's undo history.
 */
class EditorViewModel(format: String) : ViewModel() {

  private sealed interface Msg {
    data class Apply(val command: EditorCommand) : Msg
    data class AddMediaBatch(val items: List<StartMedia>) : Msg
    data object Undo : Msg
    data object Redo : Msg
  }

  private val engine = Engine(format)
  private val messages = Channel<Msg>(Channel.UNLIMITED)
  private val _uiState = MutableStateFlow(EditorUiState(engine.timeline()))
  val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

  init {
    viewModelScope.launch(Dispatchers.IO) {
      for (msg in messages) {
        when (msg) {
          is Msg.Apply ->
            runCatching { engine.apply(msg.command) }.onSuccess { timeline ->
              _uiState.value = EditorUiState(timeline)
            }
          is Msg.AddMediaBatch -> {
            // Each picked item appends after the last main-track clip; the
            // engine clamps the span into the project.
            var timeline = _uiState.value.timeline
            msg.items.forEach { item ->
              val startUs = timeline.clips.filter { it.lane == "main" }.maxOfOrNull { it.endUs } ?: 0L
              val durationUs =
                if (item.isVideo && item.durationMs > 0) item.durationMs * 1000 else 5_000_000L
              runCatching {
                engine.apply(
                  EditorCommand.AddMedia(
                    path = item.uri,
                    is_video = item.isVideo,
                    start_us = startUs,
                    duration_us = durationUs,
                  ),
                )
              }.onSuccess { next ->
                timeline = next
                _uiState.value = EditorUiState(next)
              }
            }
          }
          Msg.Undo -> engine.undo()?.let { timeline -> _uiState.value = EditorUiState(timeline) }
          Msg.Redo -> engine.redo()?.let { timeline -> _uiState.value = EditorUiState(timeline) }
        }
      }
    }
  }

  /** Applies one edit; one call is one undo step inside the engine. */
  fun apply(command: EditorCommand) {
    messages.trySend(Msg.Apply(command))
  }

  /** Appends every picked media item to the main track, in pick order. */
  fun addMediaBatch(items: List<StartMedia>) {
    messages.trySend(Msg.AddMediaBatch(items))
  }

  /** Undoes the last edit; a no-op when the engine has nothing to undo. */
  fun undo() {
    messages.trySend(Msg.Undo)
  }

  /** Re-applies the last undone edit; a no-op when there is nothing to redo. */
  fun redo() {
    messages.trySend(Msg.Redo)
  }

  /** Selects a clip on the timeline (or clears the selection). */
  fun select(clipId: Long?) {
    _uiState.value = _uiState.value.copy(selectedClipId = clipId)
  }
}
