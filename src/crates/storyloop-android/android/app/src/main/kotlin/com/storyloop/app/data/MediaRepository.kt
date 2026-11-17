package com.storyloop.app.data

import android.net.Uri

/** A photo or video stored on the device, shown as a thumbnail in pickers. */
data class RecentMedia(
  val uri: Uri,
  val isVideo: Boolean,
  val durationMs: Long?,
  /** Epoch seconds when added; used for newest-first ordering. */
  val addedAt: Long = 0,
  /** The device folder (bucket) the image lives in, when known. */
  val bucket: String? = null,
)

/** A device media folder (bucket) the picker shows as a tab. */
data class MediaBucket(val name: String, val count: Int)

/** Source of the device's media for the pickers. */
interface MediaRepository {
  /** Most recent images + videos the user captured, newest first. */
  suspend fun recents(limit: Int = 8): List<RecentMedia>

  /** Recent images from the Screenshots folder, newest first. */
  suspend fun screenshots(limit: Int = 60): List<RecentMedia>

  /** The device's image folders, biggest first. */
  suspend fun imageBuckets(limit: Int = 6): List<MediaBucket>

  /** Recent images from one device folder, newest first. */
  suspend fun imagesIn(bucket: String, limit: Int = 60): List<RecentMedia>
}
