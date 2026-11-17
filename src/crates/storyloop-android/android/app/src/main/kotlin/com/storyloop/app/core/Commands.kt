package com.storyloop.app.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * One edit the UI asks the Rust engine for. Mirrors `storyloop-core`'s
 * `Command` exactly: serde reads these as a `"type"`-tagged enum, so the
 * serial names are the Rust variants' snake_case names and the fields keep
 * their bridge names (snake_case, microseconds for time).
 */
@Serializable
sealed interface EditorCommand {
  /** Change the project's length on the timeline. */
  @Serializable
  @SerialName("set_duration")
  data class SetDuration(val duration_us: Long) : EditorCommand

  /** Add a photo or video to the main track. */
  @Serializable
  @SerialName("add_media")
  data class AddMedia(val path: String, val is_video: Boolean, val start_us: Long, val duration_us: Long) : EditorCommand

  /** Add a text overlay. */
  @Serializable
  @SerialName("add_text")
  data class AddText(val content: String, val at_us: Long, val duration_us: Long) : EditorCommand

  /** Change what a text clip says. */
  @Serializable
  @SerialName("update_text")
  data class UpdateText(val id: Long, val content: String) : EditorCommand

  /** Add an emoji sticker. */
  @Serializable
  @SerialName("add_sticker")
  data class AddSticker(val glyph: String, val at_us: Long, val duration_us: Long) : EditorCommand

  /** Move a text or sticker clip on the canvas; positions are normalized (0..1). */
  @Serializable
  @SerialName("move_clip")
  data class MoveClip(val id: Long, val x: Float, val y: Float) : EditorCommand

  /** Move and resize a clip in time. */
  @Serializable
  @SerialName("trim_clip")
  data class TrimClip(val id: Long, val start_us: Long, val duration_us: Long) : EditorCommand

  /** Remove a clip. */
  @Serializable
  @SerialName("delete_clip")
  data class DeleteClip(val id: Long) : EditorCommand
}

/** The JSON encoder matching serde's tagged-enum shape at the bridge. */
private val commandJson = Json { encodeDefaults = true }

/** This command as the JSON string the engine reads. */
fun EditorCommand.encode(): String = commandJson.encodeToString(this)
