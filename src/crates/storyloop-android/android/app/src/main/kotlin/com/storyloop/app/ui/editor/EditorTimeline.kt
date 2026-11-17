package com.storyloop.app.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Image
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.storyloop.app.R
import com.storyloop.app.core.EditorClip

/** Timeline scale: one second of project time is this many dp. */
private val DP_PER_SECOND = 25.dp

/** A row of the timeline, in display order. */
private enum class TimelineRow(val height: Dp, val icon: ImageVector) {
  Duration(48.dp, Icons.Outlined.Schedule),
  Text(44.dp, Icons.Outlined.TextFields),
  Media(56.dp, Icons.Outlined.Image),
  Sticker(44.dp, Icons.Outlined.EmojiEmotions),
}

/** The timeline: a ruler, the duration chip, and one row per lane with its clips. */
@Composable
internal fun EditorTimeline(state: EditorUiState, viewModel: EditorViewModel) {
  val mediaClips = state.timeline.clips.filter { it.lane == "main" }
  val stickerClips = state.timeline.clips.filter { it.lane == "sticker" }
  val rows =
    buildList {
      add(TimelineRow.Duration)
      add(TimelineRow.Text)
      if (mediaClips.isNotEmpty()) add(TimelineRow.Media)
      if (stickerClips.isNotEmpty()) add(TimelineRow.Sticker)
    }
  val totalWidth = DP_PER_SECOND * (state.timeline.durationUs / 1_000_000f)
  val scroll = rememberScrollState()

  Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
    // The fixed rail: one icon per visible row, heights matching the rows.
    Column(modifier = Modifier.width(52.dp)) {
      rows.forEach { row ->
        Box(modifier = Modifier.height(row.height), contentAlignment = Alignment.Center) {
          Icon(
            imageVector = row.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
          )
        }
      }
    }
    // Ruler and rows scroll together horizontally.
    Column(modifier = Modifier.weight(1f).horizontalScroll(scroll)) {
      Ruler(state.timeline.durationUs, totalWidth)
      rows.forEach { row ->
        Box(modifier = Modifier.width(totalWidth).height(row.height)) {
          when (row) {
            TimelineRow.Duration -> DurationChip(state.timeline.durationUs)
            TimelineRow.Text ->
              LaneContent(state.timeline.clips.filter { it.lane == "text" }, state, viewModel, row.height) { clip ->
                Text(
                  text = clip.text.orEmpty(),
                  color = MaterialTheme.colorScheme.onSurface,
                  fontSize = 13.sp,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.padding(horizontal = 8.dp),
                )
              }
            TimelineRow.Media ->
              LaneContent(mediaClips, state, viewModel, row.height) { clip ->
                AsyncImage(
                  model = clip.mediaPath,
                  contentDescription = null,
                  contentScale = ContentScale.Crop,
                  modifier = Modifier.fillMaxSize(),
                )
              }
            TimelineRow.Sticker ->
              LaneContent(stickerClips, state, viewModel, row.height) { clip ->
                Text(text = clip.glyph.orEmpty(), fontSize = 22.sp)
              }
          }
        }
      }
    }
  }
}

/** Second ticks; a label every five seconds. */
@Composable
private fun Ruler(durationUs: Long, totalWidth: Dp) {
  val seconds = (durationUs / 1_000_000).toInt()
  Box(modifier = Modifier.width(totalWidth).height(22.dp)) {
    for (s in 0..seconds) {
      val major = s % 5 == 0
      Box(
        modifier =
          Modifier.offset(x = DP_PER_SECOND * s)
            .width(1.5.dp)
            .height(if (major) 10.dp else 6.dp)
            .background(Color(0xFF6E6E73))
            .align(Alignment.BottomStart),
      )
      if (major) {
        Text(
          text = "${s}s",
          fontSize = 10.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.offset(x = DP_PER_SECOND * s + 4.dp, y = (-2).dp),
        )
      }
    }
  }
}

/** The "Duration: 15.0s" chip at the top of the timeline. */
@Composable
private fun DurationChip(durationUs: Long) {
  Row(
    modifier =
      Modifier.padding(start = 4.dp, top = 6.dp, bottom = 6.dp)
        .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(10.dp))
        .padding(horizontal = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = stringResource(R.string.timeline_duration_label, secondsLabel(durationUs)),
      fontSize = 13.sp,
      color = MaterialTheme.colorScheme.onSurface,
    )
  }
}

/** Positions this lane's clips along the row; `content` draws inside a clip chip. */
@Composable
private fun LaneContent(
  clips: List<EditorClip>,
  state: EditorUiState,
  viewModel: EditorViewModel,
  rowHeight: Dp,
  content: @Composable (EditorClip) -> Unit,
) {
  clips.forEach { clip ->
    val selected = clip.id == state.selectedClipId
    Box(
      modifier =
        Modifier.offset(x = DP_PER_SECOND * (clip.startUs / 1_000_000f))
          .width((DP_PER_SECOND * (clip.durationUs / 1_000_000f)).coerceAtLeast(40.dp))
          .height(rowHeight)
          .padding(2.dp)
          .clip(RoundedCornerShape(8.dp))
          .background(MaterialTheme.colorScheme.surfaceContainerHigh)
          .then(
            if (selected) {
              Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
            } else {
              Modifier
            },
          )
          .clickable { viewModel.select(clip.id) },
      contentAlignment = Alignment.Center,
    ) {
      content(clip)
    }
  }
}

/** "15.0s", the way the reference editors label durations. */
private fun secondsLabel(durationUs: Long): String = "%.1fs".format(durationUs / 1_000_000.0)
