package com.storyloop.app.ui.editor

import android.graphics.Bitmap
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.storyloop.app.R
import com.storyloop.app.core.EditorClip
import com.storyloop.app.core.EditorCommand
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** The timeline's colours, from CapCraft's palette. */
private object Palette {
  val Timeline = Color(0xFF1C1C1C)
  val HintRow = Color(0xFF262626)
  val HintIcon = Color(0xFF6B6B70)
  val Text = Color(0xFFFFFFFF)
  val TextSecondary = Color(0xFFA3A3A3)
  val TextClip = Color(0xFFE67300)
  val StickerClip = Color(0xFFDCB108)
  val AudioClip = Color(0xFF2A5256)
  val AudioWave = Color(0xFF00B2C2)
  val OverlayClip = Color(0xFF2B2B2B)
  val EffectClip = Color(0xFF7E61EE)
  val Accent = Color(0xFF00CAE0)
}

/** The timeline's fixed sizes (in dp): one second is 50dp at zoom 1, like CapCraft. */
private object Track {
  /** The band the time labels sit in, at the top. */
  const val RULER = 18f

  /** From the labels down to the main track. */
  const val RULER_GAP = 23f
  const val MAIN = 50f
  const val ROW = 30f

  /** The "add audio" and "add text" rows. */
  const val ADD_ROW = 30f

  /** Under the main track, before the first row. */
  const val FIRST_GAP = 14f
  const val GAP = 8f
  const val DP_PER_SECOND = 50f
  const val HANDLE = 14f
  const val MIN_ZOOM = 0.1f
  const val MAX_ZOOM = 20f
}

/** One clip as the timeline draws it. */
private data class ClipView(
  val id: Long,
  val startUs: Long,
  val durationUs: Long,
  /** "video", "image", "audio", "text", "sticker" or "draw". */
  val kind: String,
  val path: String?,
  val text: String?,
  val glyph: String?,
  val name: String?,
) {
  val endUs: Long
    get() = startUs + durationUs
}

/** One row of clips, one kind each: the main track first, then overlays. */
private data class RowView(val kind: String, val clips: List<ClipView>)

/** The row kinds under the main track, in the order the timeline lists them. */
private val KIND_ORDER = listOf("sticker", "audio", "text", "draw")

/** The timeline's rows: the main track, then one row per kind that has clips. */
private fun timelineRows(clips: List<EditorClip>): List<RowView> {
  val byKind = clips.groupBy { it.lane }
  fun views(lane: String) =
    (byKind[lane] ?: emptyList()).map { c ->
      ClipView(
        id = c.id,
        startUs = c.startUs,
        durationUs = c.durationUs,
        kind =
          when {
            c.kind == "media" && c.isVideo == true -> "video"
            c.kind == "media" -> "image"
            c.kind == "audio" -> "audio"
            c.kind == "text" -> "text"
            c.kind == "sticker" -> "sticker"
            else -> "draw"
          },
        path = c.mediaPath,
        text = c.text,
        glyph = c.glyph,
        name = c.name,
      )
    }
  return listOf(RowView("main", views("main"))) + KIND_ORDER.mapNotNull { kind ->
    val clipsOf = views(kind)
    if (clipsOf.isEmpty()) null else RowView(kind, clipsOf)
  }
}

/** Ruler steps, seconds: the first whose spacing is at least half a second's base width is used. */
private val STEPS =
  doubleArrayOf(1.0 / 30, 0.05, 1.0 / 12, 1.0 / 6, 0.25, 0.5, 1.0, 1.5, 2.5, 5.0, 10.0, 15.0, 30.0, 60.0, 90.0, 150.0, 300.0, 600.0, 900.0)

/** Where everything is drawn, for one frame of the timeline. */
private class Layout(
  val width: Float,
  val height: Float,
  val dp: Float,
  val playhead: Long,
  val pxPerUs: Float,
  val vScroll: Float,
  val rows: List<RowView>,
) {
  val rulerH = Track.RULER * dp
  val mainTop = (Track.RULER + Track.RULER_GAP) * dp
  val mainH = Track.MAIN * dp
  val rowH = Track.ROW * dp
  val gap = Track.GAP * dp
  val addH = Track.ADD_ROW * dp

  /** The hint rows shown until there is sound, and until there are titles. */
  val showAddAudio = rows.firstOrNull()?.clips?.isNotEmpty() == true && rows.none { it.kind == "audio" }
  val showAddText = rows.firstOrNull()?.clips?.isNotEmpty() == true && rows.none { it.kind == "text" }
  private val adds = (if (showAddAudio) 1 else 0) + (if (showAddText) 1 else 0)

  /** Each row's top before scrolling, and past the last the top of what follows the rows. */
  private val tops = FloatArray(maxOf(rows.size + 1, 2)).also { t ->
    var y = mainTop + mainH
    var below = 0
    for (i in 1..rows.size) {
      val before = when {
        i == rows.size -> if (below == 0) Track.FIRST_GAP * dp else gap
        below == 0 -> Track.FIRST_GAP * dp
        else -> gap
      }
      t[i] = y + before
      if (i < rows.size) {
        y = t[i] + rowHeight(i)
        below++
      }
    }
  }

  fun x(t: Long): Float = width / 2f + (t - playhead) * pxPerUs

  fun time(x: Float): Long = playhead + ((x - width / 2f) / pxPerUs).toLong()

  /** The tops of the rows under the main track: the rows first, then the add rows under them. */
  fun rowTop(index: Int): Float =
    if (index == 0) mainTop - vScroll else tops[index.coerceIn(1, rows.size.coerceAtLeast(1))] - vScroll

  private fun hintTop(slot: Int): Float = rowTop(max(1, rows.size)) + slot * (addH + gap)

  fun rowHeight(index: Int): Float = if (index == 0) mainH else rowH

  /** How far the rows can scroll up. */
  val maxScroll: Float
    get() = max(0f, hintTop(adds) + vScroll + 24 * dp - height)

  fun clipRect(rowIndex: Int, clip: ClipView): Rect {
    val top = rowTop(rowIndex)
    val left = x(clip.startUs)
    val right = x(clip.startUs + clip.durationUs)
    return Rect(left, top, right, top + rowHeight(rowIndex))
  }

  /** The clip at a point, with its row index: the one drawn on top where clips of a row overlap. */
  fun hit(at: Offset): Pair<Int, ClipView>? {
    rows.forEachIndexed { i, row ->
      row.clips.lastOrNull { c -> clipRect(i, c).contains(at) }?.let { return i to it }
    }
    return null
  }

  /** The add button, pinned at the right of the main row. */
  val addButton: Rect
    get() {
      val s = 36 * dp
      val top = mainTop - vScroll + (mainH - s) / 2
      return Rect(width - s - 12 * dp, top, width - 12 * dp, top + s)
    }

  /** The "add audio" hint row, when there is no sound yet: from the start to the right edge. */
  val addAudioRow: Rect?
    get() {
      if (!showAddAudio) return null
      val top = hintTop(0)
      return Rect(x(0), top, width, top + addH)
    }

  /** The "add text" hint row, when there are no titles yet. */
  val addTextRow: Rect?
    get() {
      if (!showAddText) return null
      val top = hintTop(if (showAddAudio) 1 else 0)
      return Rect(x(0), top, width, top + addH)
    }

  /** The small icon tile at the left of a hint row, under the add button. */
  fun hintTile(row: Rect): Rect {
    val right = x(0) - (Track.HANDLE + 10) * dp
    return Rect(right - 29 * dp, row.top, right, row.bottom)
  }

  /** The tile at the left of a content row (its kind's icon). */
  fun kindTile(index: Int): Rect {
    val top = rowTop(index)
    val right = x(0) - (Track.HANDLE + 10) * dp
    return Rect(right - 29 * dp, top, right, top + rowHeight(index))
  }
}

/**
 * The timeline, CapCraft's: one canvas, one mapping `x(t) = centre + (t - playhead) * pxPerUs`
 * every element draws by, so no row can ever fall out of step. A drag scrubs (playback stops),
 * two fingers zoom, a selected clip trims from white edge handles, a long press picks a clip up
 * (snapping to the playhead and other clips' ends), and empty projects get "add audio" and
 * "add text" hint rows.
 */
@Composable
internal fun EditorTimeline(
  state: EditorUiState,
  viewModel: EditorViewModel,
  timeUs: Long,
  playing: Boolean,
  modifier: Modifier = Modifier,
  onAddText: () -> Unit,
  onAddMedia: () -> Unit,
  onAddSticker: () -> Unit,
  onAddAudio: () -> Unit,
) {
  val density = LocalDensity.current
  val dp = density.density
  val touchSlop = LocalViewConfiguration.current.touchSlop
  val measurer = rememberTextMeasurer()
  val scope = rememberCoroutineScope()
  val haptics = LocalHapticFeedback.current
  var zoom by remember { mutableFloatStateOf(1f) }
  var vScroll by remember { mutableFloatStateOf(0f) }

  /** A trim being dragged: clip id, head or tail, the offset so far in µs. */
  var trim by remember { mutableStateOf<Triple<Long, Boolean, Long>?>(null) }
  var fling by remember { mutableStateOf<Job?>(null) }

  /** A clip picked up by a long press. */
  var drag by remember { mutableStateOf<Drag?>(null) }

  // The live values a gesture reads, whatever the recomposition state.
  val currentUs by rememberUpdatedState(timeUs)
  val playingNow by rememberUpdatedState(playing)
  val selectedNow by rememberUpdatedState(state.selectedClipId)
  val durationNow by rememberUpdatedState(state.timeline.durationUs)

  val addAudioLabel = stringResource(R.string.timeline_add_audio)
  val addTextLabel = stringResource(R.string.timeline_add_text)

  val rows = remember(state.timeline) { timelineRows(state.timeline.clips) }
  // a gesture reads the rows as they are now: a press that selects a clip keeps its row
  val currentRows = rememberUpdatedState(rows)

  val plus = rememberVectorPainter(Icons.Outlined.Add)
  val musicIcon = rememberVectorPainter(Icons.Outlined.MusicNote)
  val textIcon = rememberVectorPainter(Icons.Outlined.TextFields)
  val stickerIcon = rememberVectorPainter(Icons.Outlined.EmojiEmotions)
  val drawIcon = rememberVectorPainter(Icons.Outlined.EditNote)
  val hintPlus = rememberVectorPainter(Icons.Outlined.Add)
  val hintNote = rememberVectorPainter(Icons.Outlined.MusicNote)
  val hintText = rememberVectorPainter(Icons.Outlined.TextFields)

  Box(modifier) {
    Canvas(
      Modifier
        .fillMaxSize()
        .pointerInput(viewModel) {
          awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            fling?.cancel()
            val pxPerUs0 = Track.DP_PER_SECOND * dp * zoom / 1_000_000f
            val rows = currentRows.value
            val layout0 = Layout(size.width.toFloat(), size.height.toFloat(), dp, currentUs, pxPerUs0, vScroll, rows)
            // A handle of the selected clip?
            val sel = selectedNow?.let { id ->
              rows.indexOfFirst { r -> r.clips.any { it.id == id } }.takeIf { it >= 0 }?.let { i ->
                i to rows[i].clips.first { it.id == id }
              }
            }
            val handle = sel?.let { (i, c) ->
              val r = layout0.clipRect(i, c)
              val h = Track.HANDLE * dp
              when {
                Rect(r.left - h - 6 * dp, r.top, r.left + 6 * dp, r.bottom).contains(down.position) -> c.id to true
                Rect(r.right - 6 * dp, r.top, r.right + h + 6 * dp, r.bottom).contains(down.position) -> c.id to false
                else -> null
              }
            }
            val under = if (handle == null) layout0.hit(down.position) else null
            val tracker = VelocityTracker()
            tracker.addPosition(down.uptimeMillis, down.position)
            // 0 pending, 1 scrub, 2 pinch, 3 trim, 4 vertical, 5 drag a clip
            var mode = if (handle != null) 3 else 0
            var moved = Offset.Zero
            var startSpan = 0f
            var startZoom = zoom
            var trimDelta = 0L
            var snapped: Long? = null
            var lifted = false
            // where the finger is across: an edge held against the side of the screen runs the timeline on
            var fingerX = down.position.x

            /** The trimmed edge drawn [trimDelta] from where it was, or on what it snaps to within [threshold]. */
            fun showTrim(threshold: Long) {
              val (id, head) = handle!!
              val clip = currentRows.value.flatMap { it.clips }.firstOrNull { it.id == id } ?: return
              trimDelta = trimDelta.coerceIn(trimRange(clip, head, durationNow))
              var shown = trimDelta
              val origin = if (head) clip.startUs else clip.startUs + clip.durationUs
              val target = snapTarget(origin + trimDelta, snapTargets(currentRows.value, id, currentUs), threshold)
              if (target != null) shown = (target - origin).coerceIn(trimRange(clip, head, durationNow))
              if (target != snapped && target != null) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
              snapped = target
              trim = Triple(id, head, shown)
            }

            // A press held on a clip without moving picks it up.
            if (under != null) {
              val held =
                withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                  var outcome = false
                  while (true) {
                    val e = awaitPointerEvent()
                    val c = e.changes.first()
                    if (!c.pressed) {
                      lifted = true
                      break
                    }
                    if (e.changes.count { it.pressed } > 1) break
                    moved += c.positionChange()
                    tracker.addPosition(c.uptimeMillis, c.position)
                    if (abs(moved.x) > touchSlop || abs(moved.y) > touchSlop) break
                  }
                  outcome
                } == null
              if (held) {
                mode = 5
                if (playingNow) viewModel.pause()
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.select(under.second.id)
                drag = Drag(under.second.id, under.first, 0f, 0f)
              } else if (abs(moved.x) > touchSlop || abs(moved.y) > touchSlop) {
                mode = if (abs(moved.y) > abs(moved.x) * 1.2f && layout0.maxScroll > 0f) 4 else 1
                if (mode == 1 && playingNow) viewModel.pause()
              }
            }
            while (!lifted) {
              // An edge dragged to the side of the screen would stop there with the timeline: held
              // within this of either side, the timeline scrolls on under it and the edge goes with
              // it, so an image is drawn out to a long sound's end in one drag.
              val edgeZone = 40 * dp
              val atSide = mode == 3 && (fingerX < edgeZone || fingerX > size.width - edgeZone)
              val event = if (atSide) withTimeoutOrNull(16L) { awaitPointerEvent() } else awaitPointerEvent()
              if (event == null) {
                val pxPerUs = Track.DP_PER_SECOND * dp * zoom / 1_000_000f
                // faster the nearer the side, up to about a screen's width a second
                val depth = if (fingerX < edgeZone) (edgeZone - fingerX) / edgeZone else (fingerX - (size.width - edgeZone)) / edgeZone
                val stepPx = (4 + 12 * depth.coerceIn(0f, 1f)) * dp * (if (fingerX < edgeZone) -1 else 1)
                // no further than the edge may go: at its limit the timeline stops too
                val (id, head) = handle!!
                val clip = currentRows.value.flatMap { it.clips }.firstOrNull { it.id == id }
                val step = clip?.let {
                  (trimDelta + (stepPx / pxPerUs).toLong()).coerceIn(trimRange(it, head, durationNow)) - trimDelta
                } ?: 0L
                if (step != 0L) {
                  val before = viewModel.currentTimeUs.value
                  viewModel.seek(before + step)
                  trimDelta += viewModel.currentTimeUs.value - before
                  showTrim((8 * dp / pxPerUs).toLong())
                }
                continue
              }
              val pressed = event.changes.filter { it.pressed }
              if (pressed.isEmpty()) break
              if (pressed.size >= 2 && mode != 3 && mode != 5) {
                val a = pressed[0].position
                val b = pressed[1].position
                val span = hypot(a.x - b.x, a.y - b.y)
                if (mode != 2) {
                  mode = 2
                  startSpan = span
                  startZoom = zoom
                  if (playingNow) viewModel.pause()
                } else if (startSpan > 0f) {
                  zoom = (startZoom * span / startSpan).coerceIn(Track.MIN_ZOOM, Track.MAX_ZOOM)
                }
                event.changes.forEach { it.consume() }
                continue
              }
              val change = event.changes.first()
              val delta = change.positionChange()
              fingerX = change.position.x
              moved += delta
              tracker.addPosition(change.uptimeMillis, change.position)
              val pxPerUs = Track.DP_PER_SECOND * dp * zoom / 1_000_000f
              val threshold = (8 * dp / pxPerUs).toLong()
              when (mode) {
                0 -> if (abs(moved.x) > touchSlop || abs(moved.y) > touchSlop) {
                  mode = if (abs(moved.y) > abs(moved.x) * 1.2f && layout0.maxScroll > 0f) 4 else 1
                  if (mode == 1 && playingNow) viewModel.pause()
                }
                1 -> viewModel.seek(currentUs - (delta.x / pxPerUs).toLong())
                3 -> {
                  trimDelta += (delta.x / pxPerUs).toLong()
                  showTrim(threshold)
                }
                4 -> {
                  val l = Layout(size.width.toFloat(), size.height.toFloat(), dp, currentUs, pxPerUs, vScroll, currentRows.value)
                  vScroll = (vScroll - delta.y).coerceIn(0f, l.maxScroll)
                }
                5 -> {
                  val d = drag
                  if (d != null) {
                    val raw = d.dx + delta.x
                    var shownDx = raw
                    val clip = currentRows.value.flatMap { it.clips }.firstOrNull { it.id == d.clipId }
                    if (clip != null && d.row != 0) {
                      val start = clip.startUs + (raw / pxPerUs).toLong()
                      val end = start + clip.durationUs
                      val targets = snapTargets(currentRows.value, clip.id, currentUs)
                      val shift =
                        snapTarget(start, targets, threshold)?.let { it - start }
                          ?: snapTarget(end, targets, threshold)?.let { it - end }
                      if (shift != null) shownDx = raw + shift * pxPerUs
                      val now = shift?.let { start + it }
                      if (now != snapped && now != null) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                      snapped = now
                    }
                    drag = d.copy(dx = raw, dy = d.dy + delta.y, shownDx = shownDx)
                  }
                }
              }
              if (mode != 0) change.consume()
            }
            when (mode) {
              0 -> onTap(
                currentRows.value,
                selectedNow,
                layout0,
                down.position,
                onAddMedia,
                onAddAudio,
                onAddText,
                onAddSticker,
                viewModel::select,
              )
              1 -> {
                val v = tracker.calculateVelocity().x
                val pxPerUs = Track.DP_PER_SECOND * dp * zoom / 1_000_000f
                if (abs(v) > 300f) {
                  fling = scope.launch {
                    var last = 0f
                    AnimationState(0f, -v).animateDecay(exponentialDecay(frictionMultiplier = 1.6f)) {
                      val d = value - last
                      last = value
                      viewModel.seek((currentUs + (d / pxPerUs).toLong()).coerceIn(0L, durationNow))
                    }
                  }
                }
              }
              3 -> {
                val shown = trim?.third ?: trimDelta
                trim = null
                val (id, head) = handle!!
                currentRows.value.flatMap { it.clips }.firstOrNull { it.id == id }?.let { clip ->
                  if (shown != 0L) {
                    val start = if (head) clip.startUs + shown else clip.startUs
                    val duration = if (head) clip.durationUs - shown else clip.durationUs + shown
                    viewModel.apply(EditorCommand.TrimClip(id = id, start_us = start, duration_us = duration))
                  }
                }
              }
              5 -> {
                val d = drag
                drag = null
                if (d != null) {
                  dropClip(viewModel, currentRows.value, d, Track.DP_PER_SECOND * dp * zoom / 1_000_000f)
                }
              }
            }
          }
        },
    ) {
      @Suppress("UNUSED_EXPRESSION")
      currentUs
      StoryThumbs.version.intValue // a thumb arriving redraws the pictures
      val pxPerUs = Track.DP_PER_SECOND * dp * zoom / 1_000_000f
      val layout = Layout(size.width, size.height, dp, currentUs, pxPerUs, vScroll, rows)
      drawRect(Palette.Timeline)
      drawRuler(layout, measurer)
      // the time, at the left of the ruler band
      val now = measurer.measure(clock(currentUs), TextStyle(color = Palette.Text, fontSize = 12.sp))
      val total = measurer.measure(" / " + clock(durationNow), TextStyle(color = Palette.TextSecondary, fontSize = 12.sp))
      val ty = (layout.rulerH - now.size.height) / 2f + 2 * dp
      drawRect(Palette.Timeline, Offset.Zero, Size(16 * dp + now.size.width + total.size.width + 8 * dp, layout.rulerH + 4 * dp))
      drawText(now, topLeft = Offset(16 * dp, ty))
      drawText(total, topLeft = Offset(16 * dp + now.size.width, ty))
      clipRect(top = layout.rulerH + 2 * dp) {
        // at rest, a band behind each row of overlays, sound and titles, from the start on
        rows.forEachIndexed { index, row ->
          if (index == 0 || row.kind == "sticker") return@forEachIndexed
          val top = layout.rowTop(index)
          drawRect(Palette.HintRow, Offset(layout.x(0), top), Size(size.width - layout.x(0), layout.rowHeight(index)))
        }
        rows.forEachIndexed { index, row ->
          // the selected clip last, so its handles lie over its neighbours
          row.clips.sortedBy { it.id == selectedNow }.forEach { clip ->
            var r = layout.clipRect(index, clip)
            val t = trim
            if (t != null && t.first == clip.id) {
              val dx = t.third * pxPerUs
              r =
                if (t.second) r.copy(left = min(r.left + dx, r.right - 4 * dp))
                else r.copy(right = max(r.right + dx, r.left + 4 * dp))
            }
            val d = drag
            if (d != null && d.clipId == clip.id) return@forEach
            if (r.right < 0 || r.left > size.width) return@forEach
            drawClip(row.kind, clip, clip.id == selectedNow, r, layout, measurer, musicIcon)
          }
        }
        // the clip being dragged, over the others
        drag?.let { d ->
          val row = rows.getOrNull(d.row) ?: return@let
          val clip = row.clips.firstOrNull { it.id == d.clipId } ?: return@let
          val r = layout.clipRect(d.row, clip).translate(d.shownDx, if (d.row == 0) 0f else d.dy)
          drawRoundRect(Color(0x55000000), r.topLeft + Offset(0f, 4 * dp), r.size, CornerRadius(4 * dp))
          drawClip(row.kind, clip, true, r, layout, measurer, musicIcon)
          if (d.row == 0) {
            // where it will land among the main track's clips
            val x = mainDropX(layout, row, clip, r.center.x)
            drawLine(Palette.Accent, Offset(x, r.top - 4 * dp), Offset(x, r.bottom + 4 * dp), 3 * dp)
          }
        }
        // the first row of each kind keeps its kind's tile at the left
        rows.forEachIndexed { i, row ->
          val icon =
            when (row.kind) {
              "audio" -> hintNote
              "text" -> hintText
              "sticker" -> stickerIcon
              "draw" -> drawIcon
              else -> null
            }
          if (i == 0 || icon == null || rows.indexOfFirst { it.kind == row.kind } != i) return@forEachIndexed
          val tile = layout.kindTile(i)
          if (tile.right > 0) {
            drawRoundRect(Palette.HintRow, tile.topLeft, tile.size, CornerRadius(3 * dp))
            val t = 16 * dp
            translate(tile.left + (tile.width - t) / 2, tile.top + (tile.height - t) / 2) {
              with(icon) { draw(Size(t, t), colorFilter = ColorFilter.tint(Palette.HintIcon)) }
            }
          }
        }
        listOfNotNull(
          layout.addAudioRow?.let { it to addAudioLabel },
          layout.addTextRow?.let { it to addTextLabel },
        ).forEach { (r, text) ->
          drawRect(Palette.HintRow, r.topLeft, r.size)
          val s = 14 * dp
          translate(r.left + 7 * dp, r.top + (r.height - s) / 2) {
            with(hintPlus) { draw(Size(s, s), colorFilter = ColorFilter.tint(Palette.TextSecondary)) }
          }
          val label = measurer.measure(text, TextStyle(color = Palette.TextSecondary, fontSize = 12.sp))
          drawText(label, topLeft = Offset(r.left + 7 * dp + s + 5 * dp, r.top + (r.height - label.size.height) / 2))
          val tile = layout.hintTile(r)
          if (tile.right > 0) {
            drawRoundRect(Palette.HintRow, tile.topLeft, tile.size, CornerRadius(3 * dp))
            val icon = if (text == addAudioLabel) hintNote else hintText
            val t = 16 * dp
            translate(tile.left + (tile.width - t) / 2, tile.top + (tile.height - t) / 2) {
              with(icon) { draw(Size(t, t), colorFilter = ColorFilter.tint(Palette.HintIcon)) }
            }
          }
        }
        // the add button stays at the right
        val a = layout.addButton
        drawRoundRect(Color.White, a.topLeft, a.size, CornerRadius(6 * dp))
        translate(a.left + 7 * dp, a.top + 7 * dp) {
          with(plus) { draw(Size(a.width - 14 * dp, a.height - 14 * dp), colorFilter = ColorFilter.tint(Color.Black)) }
        }
      }
      // the playhead, fixed at the centre
      val cx = size.width / 2
      drawLine(Color.White, Offset(cx, layout.rulerH + 5 * dp), Offset(cx, size.height), strokeWidth = 2 * dp)
    }
  }
}

/** A clip being dragged: where it was picked up, how far it has moved, and how far it is drawn moved (snapped). */
private data class Drag(val clipId: Long, val row: Int, val dx: Float, val dy: Float, val shownDx: Float = dx)

/**
 * How far a trimmed edge may go from where it was: never past the clip's other end (a tenth of a
 * second short of it), never past the project's ends; a still or a title stretches as far as it is
 * drawn.
 */
private fun trimRange(clip: ClipView, head: Boolean, projectUs: Long): LongRange {
  val least = 100_000L
  return if (head) {
    val earliest = -clip.startUs
    earliest..(clip.durationUs - least).coerceAtLeast(earliest)
  } else {
    val latest = (projectUs - clip.startUs - clip.durationUs).coerceAtLeast(least - clip.durationUs)
    (least - clip.durationUs).coerceAtMost(latest)..latest
  }
}

/** Times a moving edge snaps to: the playhead and every other clip's ends. */
private fun snapTargets(rows: List<RowView>, except: Long, playhead: Long): List<Long> =
  listOf(playhead) + rows.flatMap { r -> r.clips.filter { it.id != except }.flatMap { listOf(it.startUs, it.endUs) } }

/** The target nearest [time] within [threshold], or null. */
private fun snapTarget(time: Long, targets: List<Long>, threshold: Long): Long? =
  targets.minByOrNull { abs(it - time) }?.takeIf { abs(it - time) <= threshold }

/** Where a main-track clip dragged to [centerX] lands: the cut before the clip whose middle is past it. */
private fun mainDropX(layout: Layout, row: RowView, moving: ClipView, centerX: Float): Float {
  val others = row.clips.filter { it.id != moving.id }
  var t = 0L
  for (c in others) {
    val mid = layout.x(t + c.durationUs / 2)
    if (centerX < mid) return layout.x(t)
    t += c.durationUs
  }
  return layout.x(t)
}

/** Lets go of a dragged clip: main-track clips change place in the order; others move in time. */
private fun dropClip(viewModel: EditorViewModel, rows: List<RowView>, d: Drag, pxPerUs: Float) {
  val row = rows.getOrNull(d.row) ?: return
  val clip = row.clips.firstOrNull { it.id == d.clipId } ?: return
  if (d.row == 0) {
    val others = row.clips.filter { it.id != clip.id }
    val centre = clip.startUs + clip.durationUs / 2 + (d.shownDx / pxPerUs).toLong()
    var t = 0L
    var index = others.size
    for ((i, c) in others.withIndex()) {
      if (centre < t + c.durationUs / 2) {
        index = i
        break
      }
      t += c.durationUs
    }
    val from = row.clips.indexOfFirst { it.id == clip.id }
    if (index != from) {
      viewModel.apply(EditorCommand.ReorderMain(id = clip.id, index = index))
    }
    return
  }
  val start = (clip.startUs + (d.shownDx / pxPerUs).toLong()).coerceAtLeast(0)
  if (start != clip.startUs) {
    viewModel.apply(EditorCommand.TrimClip(id = clip.id, start_us = start, duration_us = clip.durationUs))
  }
}

/** What a tap on the timeline does: the add button, the hint rows, a clip, or nothing. */
private fun onTap(
  rows: List<RowView>,
  selected: Long?,
  layout: Layout,
  at: Offset,
  onAddMedia: () -> Unit,
  onAddAudio: () -> Unit,
  onAddText: () -> Unit,
  onAddSticker: () -> Unit,
  select: (Long?) -> Unit,
) {
  if (layout.addButton.contains(at)) {
    onAddMedia()
    return
  }
  layout.addAudioRow?.let { r ->
    if (r.contains(at)) {
      select(null)
      onAddAudio()
      return
    }
  }
  layout.addTextRow?.let { r ->
    if (r.contains(at)) {
      select(null)
      onAddText()
      return
    }
  }
  // a kind's tile opens that kind's picker
  rows.forEachIndexed { i, row ->
    val tile = layout.kindTile(i)
    if (tile.contains(at)) {
      when (row.kind) {
        "audio" -> {
          onAddAudio()
          return
        }
        "text" -> {
          onAddText()
          return
        }
        "sticker" -> {
          onAddSticker()
          return
        }
        "draw" -> {
          onAddText()
          return
        }
      }
    }
  }
  val hit = layout.hit(at)
  if (hit == null) {
    select(null)
    return
  }
  val clip = hit.second
  select(if (selected == clip.id) null else clip.id)
}

private fun DrawScope.drawRuler(layout: Layout, measurer: androidx.compose.ui.text.TextMeasurer) {
  val dp = layout.dp
  val secondPx = layout.pxPerUs * 1_000_000f
  val half = Track.DP_PER_SECOND * dp / 2
  val step = STEPS.firstOrNull { it * secondPx > half * 1.01f } ?: STEPS.last()
  val stepUs = (step * 1_000_000).toLong().coerceAtLeast(1)
  val first = floor(layout.time(-50f).toDouble() / stepUs).toLong().coerceAtLeast(0)
  val last = ceil(layout.time(layout.width + 50f).toDouble() / stepUs).toLong()
  val style = TextStyle(color = Palette.TextSecondary, fontSize = 11.sp)
  for (i in first..last) {
    val t = i * stepUs
    val x = layout.x(t)
    if (i % 2 == 0L) {
      val label =
        if (step < 1.0) {
          val frames = (t % 1_000_000) * 30 / 1_000_000
          if (t % 1_000_000 == 0L) mmss(t) else "${frames}f"
        } else {
          mmss(t)
        }
      val m = measurer.measure(label, style)
      drawText(m, topLeft = Offset(x - m.size.width / 2f, (layout.rulerH - m.size.height) / 2f + 2 * dp))
    } else {
      drawCircle(Palette.TextSecondary, 1.2f * dp, Offset(x, layout.rulerH / 2f + 2 * dp))
    }
  }
}

private fun mmss(us: Long): String {
  val s = us / 1_000_000
  return "%02d:%02d".format(s / 60, s % 60)
}

private fun clock(us: Long): String = mmss(us)

private fun seconds1(us: Long): String {
  val whole = us / 1_000_000
  val tenth = (us % 1_000_000) / 100_000
  return if (tenth == 0L) "${whole}s" else "%d.%ds".format(whole, tenth)
}

/** One clip, drawn by its kind, as CapCraft draws each. */
private fun DrawScope.drawClip(
  kind: String,
  clip: ClipView,
  selected: Boolean,
  r: Rect,
  layout: Layout,
  measurer: androidx.compose.ui.text.TextMeasurer,
  musicIcon: androidx.compose.ui.graphics.vector.VectorPainter,
) {
  val dp = layout.dp
  val inner = Rect(r.left + 1 * dp, r.top, r.right - 1 * dp, r.bottom)
  val radius = 4 * dp
  when (clip.kind) {
    "video", "image" -> {
      drawRoundRect(Palette.OverlayClip, inner.topLeft, inner.size, CornerRadius(radius))
      clipRectRounded(inner, radius) { drawFrames(clip, inner, layout) }
    }
    "audio" -> {
      drawRoundRect(Palette.AudioClip, inner.topLeft, inner.size, CornerRadius(radius))
      clipRectRounded(inner, radius) {
        drawWave(clip, inner, layout)
        drawLabel(clip.name.orEmpty(), musicIcon, inner, measurer, dp)
      }
    }
    "text" -> {
      drawRoundRect(Palette.TextClip, inner.topLeft, inner.size, CornerRadius(radius))
      clipRectRounded(inner, radius) { drawLabel(clip.text.orEmpty(), null, inner, measurer, dp) }
    }
    "sticker" -> {
      drawRoundRect(Palette.StickerClip, inner.topLeft, inner.size, CornerRadius(radius))
      val glyph = clip.glyph.orEmpty()
      if (glyph.isNotEmpty()) {
        val size = (inner.height * 0.6f).roundToInt().coerceAtLeast(10)
        val m = measurer.measure(glyph, TextStyle(fontSize = size.sp))
        drawText(m, topLeft = Offset((inner.left + inner.right - m.size.width) / 2, inner.top + (inner.height - m.size.height) / 2))
      }
    }
    else -> {
      drawRoundRect(Palette.EffectClip, inner.topLeft, inner.size, CornerRadius(radius))
      clipRectRounded(inner, radius) { drawLabel("Drawing", null, inner, measurer, dp) }
    }
  }
  if (selected) {
    val h = Track.HANDLE * dp
    drawRoundRect(Color.White, Offset(r.left, r.top), r.size, CornerRadius(radius), style = Stroke(2 * dp))
    // handles outside the clip, with a grip line
    drawRoundRect(Color.White, Offset(r.left - h, r.top), Size(h, r.height), CornerRadius(radius))
    drawRoundRect(Color.White, Offset(r.right, r.top), Size(h, r.height), CornerRadius(radius))
    drawLine(Color.Black, Offset(r.left - h / 2, r.top + r.height * 0.32f), Offset(r.left - h / 2, r.bottom - r.height * 0.32f), 2 * dp)
    drawLine(Color.Black, Offset(r.right + h / 2, r.top + r.height * 0.32f), Offset(r.right + h / 2, r.bottom - r.height * 0.32f), 2 * dp)
    // a main-track clip shows its length; the thinner rows keep their label clear
    if (kind == "main") {
      val label = measurer.measure(seconds1(clip.durationUs), TextStyle(color = Color.White, fontSize = 9.sp))
      drawRoundRect(
        Color(0xAA000000),
        Offset(r.left + 4 * dp, r.top + 3 * dp),
        Size(label.size.width + 8f * dp, label.size.height + 2f * dp),
        CornerRadius(3 * dp),
      )
      drawText(label, topLeft = Offset(r.left + 8 * dp, r.top + 4 * dp))
    }
  }
}

/** [text] on a chip of [fill] inside [r] from [x], in [ink]; returns where a next chip may start. */
private fun DrawScope.drawChip(
  text: String,
  x: Float,
  r: Rect,
  fill: Color,
  ink: Color,
  measurer: androidx.compose.ui.text.TextMeasurer,
  dp: Float,
): Float {
  val room = (r.right - x - 14 * dp).roundToInt()
  if (room <= 10 * dp) return r.right
  val m = measurer.measure(
    text,
    TextStyle(color = ink, fontSize = 11.sp),
    maxLines = 1,
    overflow = TextOverflow.Ellipsis,
    constraints = Constraints(maxWidth = room),
  )
  val h = m.size.height + 2 * dp
  val top = r.top + (r.height - h) / 2
  drawRoundRect(fill, Offset(x, top), Size(m.size.width + 8 * dp, h), CornerRadius(3 * dp))
  drawText(m, topLeft = Offset(x + 4 * dp, top + 1 * dp))
  return x + m.size.width + 12 * dp
}

private fun DrawScope.drawLabel(
  text: String,
  icon: androidx.compose.ui.graphics.vector.VectorPainter?,
  r: Rect,
  measurer: androidx.compose.ui.text.TextMeasurer,
  dp: Float,
) {
  val s = if (icon == null) -2 * dp else 13 * dp
  val left = max(r.left, 0f) + 6 * dp
  if (icon != null) {
    translate(left, r.top + (r.height - s) / 2) {
      with(icon) { draw(Size(s, s), colorFilter = ColorFilter.tint(Color.White)) }
    }
  }
  val room = (r.right - left - s - 10 * dp).roundToInt()
  if (room <= 8) return
  val m = measurer.measure(
    text,
    TextStyle(color = Color.White, fontSize = 11.sp),
    maxLines = 1,
    overflow = TextOverflow.Ellipsis,
    constraints = Constraints(maxWidth = room),
  )
  drawText(m, topLeft = Offset(left + s + 4 * dp, r.top + (r.height - m.size.height) / 2))
}

/** A clip's pictures: square tiles from a filmstrip of its stretch of the file. */
private fun DrawScope.drawFrames(clip: ClipView, r: Rect, layout: Layout) {
  val tile = r.height
  if (r.width <= 0f) return
  if (clip.kind == "image") {
    val pic = StoryThumbs.still(checkNotNull(clip.path), false, 0, (tile * 1.2f).roundToInt()) ?: return
    var x = r.left
    while (x < r.right) {
      if (x + tile > 0 && x < layout.width) drawCropped(pic, Rect(x, r.top, x + tile, r.bottom))
      x += tile
    }
    return
  }
  val needed = ceil(r.width / tile).toInt().coerceAtLeast(1)
  // quantised so zooming does not ask for a new strip every frame
  val count = when {
    needed <= 4 -> needed
    needed <= 8 -> 8
    needed <= 16 -> 16
    needed <= 32 -> 32
    else -> 60
  }
  val strip = StoryThumbs.strip(checkNotNull(clip.path), 0, clip.durationUs, count, (tile * 0.8f).roundToInt().coerceAtLeast(16)) ?: return
  val frameW = strip.width.toFloat() / count
  val img = strip.asImageBitmap()
  for (i in 0 until needed) {
    val x = r.left + i * tile
    if (x + tile < 0 || x > layout.width) continue
    val src = min(count - 1, (i.toLong() * count / needed).toInt())
    val srcW = min(frameW, strip.height.toFloat())
    val srcX = src * frameW + (frameW - srcW) / 2
    drawImage(
      img,
      srcOffset = IntOffset(srcX.roundToInt(), 0),
      srcSize = IntSize(srcW.roundToInt().coerceAtLeast(1), strip.height),
      dstOffset = IntOffset(x.roundToInt(), r.top.roundToInt()),
      dstSize = IntSize(tile.roundToInt(), r.height.roundToInt()),
    )
  }
}

private fun DrawScope.drawWave(clip: ClipView, r: Rect, layout: Layout) {
  val dp = layout.dp
  val bucketPx = 3 * dp
  val buckets = (r.width / bucketPx).roundToInt().coerceIn(8, 2048)
  val quant = 1 shl (32 - Integer.numberOfLeadingZeros(buckets - 1)).coerceIn(3, 11)
  val wave = StoryThumbs.wave(checkNotNull(clip.path), 0, clip.durationUs, quant) ?: return
  val w = r.width / wave.size
  val mid = r.top + r.height / 2
  for (i in wave.indices) {
    val x = r.left + i * w
    if (x < -w || x > layout.width) continue
    val h = (wave[i] * (r.height - 6 * dp)).coerceAtLeast(1 * dp)
    drawRect(Palette.AudioWave, Offset(x, mid - h / 2), Size(max(1f, w * 0.6f), h))
  }
}

private fun DrawScope.clipRectRounded(r: Rect, radius: Float, block: DrawScope.() -> Unit) {
  val path = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(r, CornerRadius(radius))) }
  clipPath(path) { block() }
}

/** Draws [b] filling [r], cropped to keep its shape, centred. */
private fun DrawScope.drawCropped(b: Bitmap, r: Rect) {
  val img = b.asImageBitmap()
  val scale = max(r.width / b.width, r.height / b.height)
  val sw = r.width / scale
  val sh = r.height / scale
  val sx = ((b.width - sw) / 2).coerceAtLeast(0f)
  val sy = ((b.height - sh) / 2).coerceAtLeast(0f)
  drawImage(
    img,
    srcOffset = IntOffset(sx.roundToInt(), sy.roundToInt()),
    srcSize = IntSize(sw.roundToInt().coerceIn(1, b.width), sh.roundToInt().coerceIn(1, b.height)),
    dstOffset = IntOffset(r.left.roundToInt(), r.top.roundToInt()),
    dstSize = IntSize(r.width.roundToInt().coerceAtLeast(1), r.height.roundToInt().coerceAtLeast(1)),
  )
}
