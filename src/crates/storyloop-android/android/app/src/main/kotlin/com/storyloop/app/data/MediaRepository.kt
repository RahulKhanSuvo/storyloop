package com.storyloop.app.data

import android.net.Uri

/** A photo or video stored on the device, shown as a recent thumbnail. */
data class RecentMedia(
  val uri: Uri,
  val isVideo: Boolean,
  val durationMs: Long?,
  /** Epoch seconds when added; used for newest-first ordering. */
  val addedAt: Long = 0,
)

/** Source of the device's recent media for the create sheet. */
interface MediaRepository {
  /** Most recent images + videos the user captured, newest first. */
  suspend fun recents(limit: Int = 8): List<RecentMedia>
}
