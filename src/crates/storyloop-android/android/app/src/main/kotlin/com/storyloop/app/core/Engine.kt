package com.storyloop.app.core

/**
 * The editing session, talking to the Rust engine over UniFFI.
 *
 * Owns the generated `EditorSession` and converts its snapshots into the
 * Kotlin-facing types the UI uses, so no composable ever touches generated
 * bindings directly.
 */
class Engine(format: String) {

  private val session: EditorSession =
    try {
      EditorSession(format)
    } catch (e: Exception) {
      throw IllegalStateException("the engine did not start: ${e.message}", e)
    }

  /** Applies one edit; one call is one undo step inside the engine. */
  fun apply(command: EditorCommand): EditorTimeline = session.apply(command.encode()).toTimeline()

  /** Undoes the last command; null when there is nothing to undo. */
  fun undo(): EditorTimeline? =
    runCatching { session.undo().toTimeline() }.getOrNull()

  /** Re-applies the last undone command; null when there is nothing to redo. */
  fun redo(): EditorTimeline? =
    runCatching { session.redo().toTimeline() }.getOrNull()

  /** The current timeline, without changing anything. */
  fun timeline(): EditorTimeline = session.timeline().toTimeline()

  private fun TimelineView.toTimeline(): EditorTimeline =
    EditorTimeline(
      format = format,
      ratio = ratio,
      durationUs = durationUs,
      clips = clips.map { it.toClip() },
      canUndo = canUndo,
      canRedo = canRedo,
    )

  private fun ClipInfo.toClip(): EditorClip =
    EditorClip(
      id = id.toLong(),
      lane = lane,
      kind = kind,
      startUs = startUs,
      durationUs = durationUs,
      mediaPath = mediaPath,
      isVideo = isVideo,
      text = text,
      glyph = glyph,
      name = name,
      x = x,
      y = y,
    )
}

/** One clip as the editor's UI sees it. */
data class EditorClip(
  val id: Long,
  /** The lane the clip sits on: "audio", "sticker", "text", "draw" or "main". */
  val lane: String,
  /** What the clip is: "media", "text", "sticker", "draw" or "audio". */
  val kind: String,
  val startUs: Long,
  val durationUs: Long,
  /** The file on the device, for media and audio clips. */
  val mediaPath: String?,
  val isVideo: Boolean?,
  /** What a text clip says. */
  val text: String?,
  /** The emoji a sticker clip shows. */
  val glyph: String?,
  /** What an audio clip is titled. */
  val name: String?,
  /** Normalized canvas position (0..1), for text and stickers. */
  val x: Float?,
  val y: Float?,
) {
  /** Where the clip ends on the timeline, in microseconds. */
  val endUs: Long
    get() = startUs + durationUs
}

/** A full snapshot of the timeline, one per engine call. */
data class EditorTimeline(
  /** The canvas format: "reel", "portrait", "square" or "landscape". */
  val format: String,
  /** Width divided by height of the canvas. */
  val ratio: Float,
  /** The project's length, in microseconds. */
  val durationUs: Long,
  val clips: List<EditorClip>,
  val canUndo: Boolean,
  val canRedo: Boolean,
)
