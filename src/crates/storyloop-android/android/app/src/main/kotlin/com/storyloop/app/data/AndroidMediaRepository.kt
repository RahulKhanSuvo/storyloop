package com.storyloop.app.data

import android.content.ContentResolver
import android.content.ContentUris
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** MediaStore-backed device media for the pickers: recents, folders, screenshots. */
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
      Log.w(TAG, "recents query failed; pickers show empty states", t)
      // Media permission missing or query unsupported — callers fall back to empty states.
      emptyList()
    }
  }

  override suspend fun screenshots(limit: Int): List<RecentMedia> = withContext(Dispatchers.IO) {
    try {
      val bucket = MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME
      val direct = queryImages(limit, "$bucket = ?", arrayOf("Screenshots"))
      // Some builds keep the folder only in the relative path — filter in Kotlin.
      if (direct.isNotEmpty()) {
        direct
      } else {
        queryImages(limit)
          .filter { it.bucket?.contains("screenshot", ignoreCase = true) == true }
          .take(limit)
      }
    } catch (t: Throwable) {
      Log.w(TAG, "screenshots query failed", t)
      emptyList()
    }
  }

  override suspend fun imageBuckets(limit: Int): List<MediaBucket> = withContext(Dispatchers.IO) {
    try {
      val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
      val bucket = MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME
      val projection = arrayOf(MediaStore.MediaColumns._ID, bucket)
      resolver
        .query(collection, projection, null, null, "${MediaStore.MediaColumns.DATE_ADDED} DESC")
        ?.use { cursor ->
          val bucketIdx = cursor.getColumnIndex(bucket)
          val counts = LinkedHashMap<String, Int>()
          var seen = 0
          while (cursor.moveToNext() && seen < 3000) {
            seen++
            if (bucketIdx < 0 || cursor.isNull(bucketIdx)) continue
            val name = cursor.getString(bucketIdx) ?: continue
            counts[name] = (counts[name] ?: 0) + 1
          }
          counts.entries.sortedByDescending { it.value }.take(limit).map { MediaBucket(it.key, it.value) }
        }
        ?: emptyList()
    } catch (t: Throwable) {
      Log.w(TAG, "buckets query failed", t)
      emptyList()
    }
  }

  override suspend fun imagesIn(bucket: String, limit: Int): List<RecentMedia> = withContext(Dispatchers.IO) {
    try {
      val bucketCol = MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME
      queryImages(limit, "$bucketCol = ?", arrayOf(bucket))
    } catch (t: Throwable) {
      Log.w(TAG, "bucket query failed for $bucket", t)
      emptyList()
    }
  }

  private fun queryImages(limit: Int, selection: String? = null, selectionArgs: Array<String>? = null): List<RecentMedia> {
    val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    val projection =
      arrayOf(
        MediaStore.MediaColumns._ID,
        MediaStore.MediaColumns.DATE_ADDED,
        MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME,
      )
    // No LIMIT in sortOrder — some OEM MediaStore builds reject it; we cap in take().
    return resolver
      .query(collection, projection, selection, selectionArgs, "${MediaStore.MediaColumns.DATE_ADDED} DESC")
      ?.use { cursor ->
        val idIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
        val dateIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
        val bucketIdx = cursor.getColumnIndex(MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME)
        buildList {
          while (cursor.moveToNext() && size < limit) {
            this +=
              RecentMedia(
                uri = ContentUris.withAppendedId(collection, cursor.getLong(idIdx)),
                isVideo = false,
                durationMs = null,
                addedAt = cursor.getLong(dateIdx),
                bucket = if (bucketIdx >= 0 && !cursor.isNull(bucketIdx)) cursor.getString(bucketIdx) else null,
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
