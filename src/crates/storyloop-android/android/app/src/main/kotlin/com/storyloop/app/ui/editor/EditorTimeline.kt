package com.storyloop.app.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.storyloop.app.R
import com.storyloop.app.core.EditorClip

/** The playhead color from the reference timeline. */
private val TimelineYellow = Color(0xFFFFD60A)

/** Ruler scale: one second of project time is this many dp. */
private val RULER_DP_PER_SECOND = 18.dp

/** The fixed rail's width; rows start (and the playhead sits) right of it. */
private val RAIL_WIDTH = 64.dp

/**
 * The timeline panel, per owner reference: a rounded-top surface holding a dot
 * ruler with a yellow playhead, a rail of per-row buttons (the clock is
 * decorative; T+ and the media+ open their panels), the duration chip, a text
 * row, and one filmstrip row per media clip.
 */
@Composable
internal fun EditorTimeline(
  state: EditorUiState,
  viewModel: EditorViewModel,
  onAddText: () -> Unit,
  onAddMedia: () -> Unit,
  onAddSticker: () -> Unit,
) {
  val mediaClips = state.timeline.clips.filter { it.lane == "main" }
  val textClips = state.timeline.clips.filter { it.lane == "text" }
  val stickerClips = state.timeline.clips.filter { it.lane == "sticker" }

  Column(
    modifier =
      Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
        .background(MaterialTheme.colorScheme.surfaceContainerLow)
        .padding(top = 2.dp, bottom = 10.dp),
  ) {
    Ruler(state.timeline.durationUs)
    TimelineRow(rowHeight = 52.dp, rail = { RailButton(Icons.Outlined.Schedule, null) }) {
      DurationChip(state.timeline.durationUs)
    }
    TimelineRow(
      rowHeight = 48.dp,
      rail = { RailButton(Icons.Outlined.TextFields, stringResource(R.string.editor_tool_text), onAddText) },
    ) {
      if (textClips.isEmpty()) {
        RowPlaceholder(stringResource(R.string.timeline_text_placeholder))
      } else {
        Column {
          textClips.forEach { clip -> TextChip(clip, state, viewModel) }
        }
      }
    }
    if (stickerClips.isNotEmpty()) {
      TimelineRow(
        rowHeight = 48.dp,
        rail = {
          RailButton(Icons.Outlined.EmojiEmotions, stringResource(R.string.editor_tool_stickers), onAddSticker)
        },
      ) {
        Row {
          stickerClips.forEach { clip -> StickerChip(clip, state, viewModel) }
        }
      }
    }
    mediaClips.forEach { clip ->
      TimelineRow(
        rowHeight = 56.dp,
        rail = { RailButton(Icons.Outlined.AddPhotoAlternate, stringResource(R.string.editor_tool_media), onAddMedia) },
      ) {
        Filmstrip(clip, state, viewModel)
      }
    }
  }
}

/** One timeline row: the fixed rail button on the left, the row's content right of the playhead. */
@Composable
private fun TimelineRow(rowHeight: Dp, rail: @Composable () -> Unit, content: @Composable () -> Unit) {
  Row {
    Box(modifier = Modifier.width(RAIL_WIDTH).height(rowHeight), contentAlignment = Alignment.Center) {
      rail()
    }
    Box(modifier = Modifier.weight(1f).height(rowHeight)) {
      // The playhead runs down the panel's left edge, at the project's current time.
      Box(
        modifier =
          Modifier.align(Alignment.CenterStart).width(2.dp).fillMaxHeight().background(TimelineYellow),
      )
      Box(modifier = Modifier.fillMaxSize().padding(start = 10.dp, end = 10.dp)) {
        content()
      }
    }
  }
}

/** A rail button; no click action when `onClick` is null (like the duration clock). */
@Composable
private fun RailButton(icon: ImageVector, contentDescription: String?, onClick: (() -> Unit)? = null) {
  Icon(
    imageVector = icon,
    contentDescription = contentDescription,
    tint = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier =
      Modifier.size(24.dp).then(
        if (onClick != null) {
          Modifier.clickable { onClick() }
        } else {
          Modifier
        },
      ),
  )
}

/** Second ticks as dots; a label every five seconds; the playhead at 0s. */
@Composable
private fun Ruler(durationUs: Long) {
  val seconds = (durationUs / 1_000_000).toInt()
  Row {
    Spacer(Modifier.width(RAIL_WIDTH))
    Box(modifier = Modifier.weight(1f).height(30.dp)) {
      Box(
        modifier =
          Modifier.align(Alignment.CenterStart).width(2.dp).fillMaxHeight().background(TimelineYellow),
      )
      for (s in 0..seconds) {
        val major = s % 5 == 0
        Box(
          modifier =
            Modifier.align(Alignment.BottomStart)
              .offset(x = RULER_DP_PER_SECOND * s)
              .size(if (major) 5.dp else 3.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.onSurfaceVariant),
        )
        if (major) {
          Text(
            text = "${s}s",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.offset(x = RULER_DP_PER_SECOND * s + 8.dp),
          )
        }
      }
    }
  }
}

/** The "Duration: 15.0s" chip row. */
@Composable
private fun DurationChip(durationUs: Long) {
  Box(
    modifier =
      Modifier.fillMaxWidth()
        .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp))
        .padding(horizontal = 14.dp, vertical = 13.dp),
  ) {
    Text(
      text = stringResource(R.string.timeline_duration_label, "%.1fs".format(durationUs / 1_000_000.0)),
      fontSize = 15.sp,
      color = MaterialTheme.colorScheme.onSurface,
    )
  }
}

/** An empty overlay lane shows its name as a placeholder, like the reference. */
@Composable
private fun RowPlaceholder(label: String) {
  Box(
    modifier =
      Modifier.fillMaxWidth()
        .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp))
        .padding(horizontal = 14.dp, vertical = 13.dp),
  ) {
    Text(text = label, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

/** A text clip on the text row. */
@Composable
private fun TextChip(clip: EditorClip, state: EditorUiState, viewModel: EditorViewModel) {
  val selected = clip.id == state.selectedClipId
  Box(
    modifier =
      Modifier.fillMaxWidth()
        .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp))
        .then(
          if (selected) Modifier.border(1.5.dp, TimelineYellow, RoundedCornerShape(12.dp)) else Modifier
        )
        .clickable { viewModel.select(clip.id) }
        .padding(horizontal = 14.dp, vertical = 13.dp),
  ) {
    Text(
      text = clip.text.orEmpty(),
      fontSize = 15.sp,
      color = MaterialTheme.colorScheme.onSurface,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

/** A sticker clip on the sticker row. */
@Composable
private fun StickerChip(clip: EditorClip, state: EditorUiState, viewModel: EditorViewModel) {
  val selected = clip.id == state.selectedClipId
  Box(
    modifier =
      Modifier.size(width = 56.dp, height = 40.dp)
        .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(10.dp))
        .then(
          if (selected) Modifier.border(1.5.dp, TimelineYellow, RoundedCornerShape(10.dp)) else Modifier
        )
        .clickable { viewModel.select(clip.id) },
    contentAlignment = Alignment.Center,
  ) {
    Text(text = clip.glyph.orEmpty(), fontSize = 20.sp)
  }
}

/**
 * One media clip as a filmstrip: its thumbnail tiled across the row, like the
 * reference. Tapping selects the clip.
 */
@Composable
private fun Filmstrip(clip: EditorClip, state: EditorUiState, viewModel: EditorViewModel) {
  val selected = clip.id == state.selectedClipId
  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val tile = 56.dp
    val tiles = (maxWidth / tile).toInt() + 1
    Row(
      modifier =
        Modifier.fillMaxSize()
          .clip(RoundedCornerShape(10.dp))
          .clickable { viewModel.select(clip.id) },
    ) {
      repeat(tiles) {
        AsyncImage(
          model = clip.mediaPath,
          contentDescription = null,
          contentScale = ContentScale.Crop,
          modifier = Modifier.width(tile).fillMaxHeight(),
        )
      }
    }
    if (selected) {
      Box(modifier = Modifier.fillMaxSize().border(1.5.dp, TimelineYellow, RoundedCornerShape(10.dp)))
    }
  }
}
