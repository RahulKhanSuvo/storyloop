package com.storyloop.app.ui.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import android.util.LruCache
import androidx.compose.runtime.mutableIntStateOf
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The timeline's pictures and waveforms, made off the main thread and kept in
 * memory, like CapCraft's `Thumbs`. A miss starts the work and returns null;
 * [version] changes when anything arrives, so the timeline redraws.
 */
object StoryThumbs {
  val version = mutableIntStateOf(0)
  private var context: Context? = null
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val strips = object : LruCache<String, Bitmap>(64 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
  }
  private val waves = LruCache<String, FloatArray>(256)
  private val pending = ConcurrentHashMap.newKeySet<String>()

  fun init(context: Context) {
    this.context = context.applicationContext
  }

  /** A still (a photo, or a frame of a video at [atUs]) [width] pixels across. */
  fun still(path: String, isVideo: Boolean, atUs: Long, width: Int): Bitmap? {
    val key = "p|$path|$atUs|$width"
    strips.get(key)?.let { return it }
    if (pending.add(key)) {
      scope.launch {
        runCatching { loadStill(path, isVideo, atUs, width) }
          .onFailure { Log.w("StoryThumbs", "still failed: $path", it) }
          .getOrNull()?.let { strips.put(key, it) }
        pending.remove(key)
        version.intValue++
      }
    }
    return null
  }

  /** A strip of [count] square frames of a video between two source times, [height] pixels tall. */
  fun strip(path: String, fromUs: Long, toUs: Long, count: Int, height: Int): Bitmap? {
    val key = "s|$path|$fromUs|$toUs|$count|$height"
    strips.get(key)?.let { return it }
    if (pending.add(key)) {
      scope.launch {
        runCatching { loadStrip(path, fromUs, toUs, count, height) }
          .onFailure { Log.w("StoryThumbs", "strip failed: $path", it) }
          .getOrNull()?.let { strips.put(key, it) }
        pending.remove(key)
        version.intValue++
      }
    }
    return null
  }

  /** [buckets] peaks of an audio file between two source times, each 0..1. */
  fun wave(path: String, fromUs: Long, toUs: Long, buckets: Int): FloatArray? {
    val key = "w|$path|$fromUs|$toUs|$buckets"
    waves.get(key)?.let { return it }
    if (pending.add(key)) {
      scope.launch {
        runCatching { loadWave(path, fromUs, toUs, buckets) }
          .onFailure { Log.w("StoryThumbs", "wave failed: $path", it) }
          .getOrNull()?.let { waves.put(key, it) }
        pending.remove(key)
        version.intValue++
      }
    }
    return null
  }

  private fun loadStill(path: String, isVideo: Boolean, atUs: Long, width: Int): Bitmap? = if (isVideo) {
    val r = MediaMetadataRetriever()
    try {
      setSource(r, path)
      val frame = r.getFrameAtTime(atUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return null
      scaleTo(frame, width)
    } finally {
      r.release()
    }
  } else {
    val ctx = checkNotNull(context)
    val resolver = ctx.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    open(path, resolver)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= width) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    open(path, resolver)?.use { BitmapFactory.decodeStream(it, null, options) }
  }

  private fun loadStrip(path: String, fromUs: Long, toUs: Long, count: Int, height: Int): Bitmap? {
    val r = MediaMetadataRetriever()
    try {
      setSource(r, path)
      val strip = Bitmap.createBitmap(count * height, height, Bitmap.Config.ARGB_8888)
      val canvas = Canvas(strip)
      val paint = Paint(Paint.FILTER_BITMAP_FLAG)
      for (i in 0 until count) {
        val t = fromUs + if (count == 1) 0L else i * (toUs - fromUs) / (count - 1)
        val frame = r.getFrameAtTime(t, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: continue
        val square = cropSquare(frame, height)
        canvas.drawBitmap(square, (i * height).toFloat(), 0f, paint)
        if (square !== frame) square.recycle()
      }
      return strip
    } finally {
      r.release()
    }
  }

  /** One decoded PCM peak row: the loudest sample of each bucket of the span. */
  private fun loadWave(path: String, fromUs: Long, toUs: Long, buckets: Int): FloatArray? {
    val ctx = checkNotNull(context)
    val extractor = MediaExtractor()
    var codec: MediaCodec? = null
    try {
      if (path.startsWith("content:")) {
        val pfd = ctx.contentResolver.openFileDescriptor(Uri.parse(path), "r") ?: return null
        pfd.use { extractor.setDataSource(it.fileDescriptor) }
      } else {
        extractor.setDataSource(path)
      }
      var track = -1
      var format: MediaFormat? = null
      for (i in 0 until extractor.trackCount) {
        val f = extractor.getTrackFormat(i)
        if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
          track = i
          format = f
          break
        }
      }
      val audio = format ?: return null
      extractor.selectTrack(track)
      extractor.seekTo(fromUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
      val mime = checkNotNull(audio.getString(MediaFormat.KEY_MIME))
      codec = MediaCodec.createDecoderByType(mime)
      codec.configure(audio, null, null, 0)
      codec.start()

      val peaks = FloatArray(buckets)
      val spanUs = (toUs - fromUs).coerceAtLeast(1)
      val info = MediaCodec.BufferInfo()
      var inputDone = false
      var outputDone = false
      var seenOutput = false
      var channels = audio.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
      var encoding = audio.getInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
      var guard = 0
      while (!outputDone && guard++ < 4000) {
        if (!inputDone) {
          val inIndex = codec.dequeueInputBuffer(10_000L)
          if (inIndex >= 0) {
            val input = checkNotNull(codec.getInputBuffer(inIndex))
            val size = extractor.readSampleData(input, 0)
            if (size < 0) {
              codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
              inputDone = true
            } else {
              val timeUs = extractor.sampleTime
              if (timeUs > toUs) {
                codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                inputDone = true
                extractor.advance()
              } else {
                codec.queueInputBuffer(inIndex, 0, size, timeUs, 0)
                extractor.advance()
              }
            }
          }
        }
        val outIndex = codec.dequeueOutputBuffer(info, 10_000L)
        when {
          outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
            val out = codec.outputFormat
            if (out.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) channels = out.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            if (out.containsKey(MediaFormat.KEY_PCM_ENCODING)) encoding = out.getInteger(MediaFormat.KEY_PCM_ENCODING)
          }
          outIndex >= 0 -> {
            val out = checkNotNull(codec.getOutputBuffer(outIndex))
            out.order(ByteOrder.nativeOrder())
            if (info.presentationTimeUs > toUs) {
              outputDone = true
            } else if (info.size > 0) {
              seenOutput = true
              val first = maxOf(info.presentationTimeUs, fromUs)
              val rate = sampleRate(audio, codec)
              val frames = info.size / bytesPerSample(encoding) / max(1, channels)
              for (s in 0 until frames) {
                val t = first + s.toLong() * 1_000_000L / max(1, rate)
                val v = readSample(out, info.offset + s * bytesPerSample(encoding) * channels, encoding)
                val index = ((t - fromUs) * buckets / spanUs).toInt().coerceIn(0, buckets - 1)
                peaks[index] = max(peaks[index], v)
              }
            }
            codec.releaseOutputBuffer(outIndex, false)
            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
          }
        }
      }
      return if (seenOutput) peaks else null
    } finally {
      runCatching { codec?.stop() }
      runCatching { codec?.release() }
      extractor.release()
    }
  }

  private fun bytesPerSample(encoding: Int): Int = when (encoding) {
    AudioFormat.ENCODING_PCM_FLOAT -> 4
    AudioFormat.ENCODING_PCM_32BIT -> 4
    AudioFormat.ENCODING_PCM_8BIT -> 1
    else -> 2
  }

  private fun sampleRate(format: MediaFormat, codec: MediaCodec): Int =
    if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
    else codec.outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)

  /** One sample of every channel merged, as 0..1. */
  private fun readSample(buffer: java.nio.ByteBuffer, at: Int, encoding: Int): Float {
    var v = 0f
    when (encoding) {
      AudioFormat.ENCODING_PCM_FLOAT -> v = abs(buffer.getFloat(at))
      AudioFormat.ENCODING_PCM_32BIT -> v = abs(buffer.getInt(at)) / 2_147_483_647f
      AudioFormat.ENCODING_PCM_8BIT -> v = abs(buffer.get(at).toInt()) / 127f
      else -> v = abs(buffer.getShort(at).toInt()) / 32_768f
    }
    return v.coerceAtMost(1f)
  }

  private fun cropSquare(b: Bitmap, size: Int): Bitmap {
    val side = minOf(b.width, b.height)
    val left = (b.width - side) / 2
    val top = (b.height - side) / 2
    val square = Bitmap.createBitmap(b, left, top, side, side)
    return if (square.width == size && square.height == size) square
    else Bitmap.createScaledBitmap(square, size, size, true)
  }

  private fun scaleTo(b: Bitmap, width: Int): Bitmap =
    if (b.width <= width) b else Bitmap.createScaledBitmap(b, width, max(1, b.height * width / b.width), true)

  private fun setSource(r: MediaMetadataRetriever, path: String) {
    if (path.startsWith("content:")) {
      r.setDataSource(checkNotNull(context), Uri.parse(path))
    } else {
      r.setDataSource(path)
    }
  }

  private fun open(path: String, resolver: android.content.ContentResolver) =
    if (path.startsWith("content:")) resolver.openInputStream(Uri.parse(path)) else java.io.FileInputStream(path)
}
