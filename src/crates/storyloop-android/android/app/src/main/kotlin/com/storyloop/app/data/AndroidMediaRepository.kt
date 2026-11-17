package com.storyloop.app.data

import android.content.ContentResolver
import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** MediaStore-backed recents feed: images + videos, newest first. */
class AndroidMediaRepository(private val resolver: ContentResolver) : MediaRepository {

  private companion object {
    const val TAG = "AndroidMediaRepo"
  }

  override suspend fun recents(limit: Int): List<RecentMedia> = withContext(Dispatchers.IO) {
    try {
      val images = queryImages(limit)
      val videos = queryVideos(limit)
      Log.i(TAG, "recents query: images=${images.size} videos=${videos.size}")
      (images + videos).sortedByDescending { it.addedAt }.take(limit)
    } catch (t: Throwable) {
      Log.w(TAG, "recents query failed; sheet will show placeholders", t)
      // Media permission missing or query unsupported — callers fall back to placeholders.
      emptyList()
    }
  }

  private fun queryImages(limit: Int): List<RecentMedia> {
    val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    val projection =
      arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DATE_ADDED)
    // No LIMIT in sortOrder — some OEM MediaStore builds reject it; we cap in take().
    return resolver
      .query(collection, projection, null, null, "${MediaStore.MediaColumns.DATE_ADDED} DESC")
      ?.use { cursor ->
        val idIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
        val dateIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
        buildList {
          while (cursor.moveToNext() && size < limit) {
            this +=
              RecentMedia(
                uri = ContentUris.withAppendedId(collection, cursor.getLong(idIdx)),
                isVideo = false,
                durationMs = null,
                addedAt = cursor.getLong(dateIdx),
              )
          }
        }
      }
      ?: emptyList()
  }

  private fun queryVideos(limit: Int): List<RecentMedia> {
    val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    val projection =
      arrayOf(
        MediaStore.MediaColumns._ID,
        MediaStore.MediaColumns.DATE_ADDED,
        MediaStore.Video.VideoColumns.DURATION,
      )
    return resolver
      .query(collection, projection, null, null, "${MediaStore.MediaColumns.DATE_ADDED} DESC")
      ?.use { cursor ->
        val idIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
        val dateIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
        val durationIdx = cursor.getColumnIndex(MediaStore.Video.VideoColumns.DURATION)
        buildList {
          while (cursor.moveToNext() && size < limit) {
            this +=
              RecentMedia(
                uri = ContentUris.withAppendedId(collection, cursor.getLong(idIdx)),
                isVideo = true,
                durationMs =
                  if (durationIdx >= 0 && !cursor.isNull(durationIdx)) cursor.getLong(durationIdx)
                  else null,
                addedAt = cursor.getLong(dateIdx),
              )
          }
        }
      }
      ?: emptyList()
  }
}
