package com.storyloop.app.ui.editor

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.storyloop.app.StartMedia
import com.storyloop.app.core.EditorCommand
import com.storyloop.app.core.EditorTimeline
import com.storyloop.app.core.Engine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
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
class EditorViewModel(format: String, private val appContext: Context) : ViewModel() {

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

  /** Adds one audio file after the last audio clip; the engine clamps the span. */
  fun addAudio(path: String, name: String, durationUs: Long) {
    val timeline = _uiState.value.timeline
    val startUs = timeline.clips.filter { it.lane == "audio" }.maxOfOrNull { it.endUs } ?: 0L
    messages.trySend(Msg.Apply(EditorCommand.AddAudio(path = path, name = name, at_us = startUs, duration_us = durationUs)))
  }

  // --- Preview playback -------------------------------------------------
  // A wall-clock ticker drives the playhead at ~60fps; the canvas, transport
  // counter and timeline playhead all follow [currentTimeUs].

  private val _playing = MutableStateFlow(false)
  val playing: StateFlow<Boolean> = _playing.asStateFlow()

  private val _currentTimeUs = MutableStateFlow(0L)
  val currentTimeUs: StateFlow<Long> = _currentTimeUs.asStateFlow()

  private var playJob: Job? = null

  /** Starts preview from the current time; from the end it restarts. Sound plays with the picture. */
  fun play() {
    if (_playing.value) return
    val duration = _uiState.value.timeline.durationUs
    if (duration <= 0) return
    var from = _currentTimeUs.value
    if (from >= duration - 100_000) from = 0L
    _currentTimeUs.value = from
    _playing.value = true
    startAudio(from)
    playJob = viewModelScope.launch {
      var t = from
      var lastNanos = System.nanoTime()
      while (isActive && t < duration) {
        delay(16)
        val now = System.nanoTime()
        t = (t + (now - lastNanos) / 1000).coerceAtMost(duration)
        lastNanos = now
        _currentTimeUs.value = t
        // the sound follows the play clock: at a clip's end the next one starts
        if (audioPlayer != null && t >= audioEndUs) {
          stopAudio()
          startAudio(t)
        }
      }
      _playing.value = false
      stopAudio()
    }
  }

  /** Pauses preview; the time stays where it stopped. */
  fun pause() {
    playJob?.cancel()
    playJob = null
    _playing.value = false
    stopAudio()
  }

  /** Scrubs: playback stops and the playhead jumps to [timeUs]. */
  fun seek(timeUs: Long) {
    pause()
    val duration = _uiState.value.timeline.durationUs
    _currentTimeUs.value = timeUs.coerceIn(0L, duration)
  }

  // --- Preview sound ----------------------------------------------------
  // While the picture plays, the audio clip under the playhead plays too,
  // from its own offset; multiple simultaneous mixes come with the Rust engine.

  private var audioPlayer: MediaPlayer? = null
  private var audioEndUs = 0L

  private fun startAudio(fromUs: Long) {
    stopAudio()
    val clip = _uiState.value.timeline.clips
      .filter { it.lane == "audio" && it.mediaPath != null }
      .lastOrNull { fromUs >= it.startUs && fromUs < it.endUs }
      ?: return
    val player = try {
      MediaPlayer.create(appContext, Uri.parse(clip.mediaPath)) ?: return
    } catch (e: Exception) {
      return
    }
    player.seekTo(((fromUs - clip.startUs) / 1_000).toInt())
    player.start()
    audioPlayer = player
    audioEndUs = clip.endUs
  }

  private fun stopAudio() {
    audioPlayer?.let { player ->
      runCatching { player.stop() }
      runCatching { player.release() }
    }
    audioPlayer = null
    audioEndUs = 0L
  }
}
