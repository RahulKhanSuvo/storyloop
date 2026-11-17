package com.storyloop.app.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.FormatColorReset
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.ViewTimeline
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.storyloop.app.R
import com.storyloop.app.StartMedia
import com.storyloop.app.core.EditorCommand
import com.storyloop.app.core.EditorTimeline

/** The action yellow from the reference screenshots. */
private val ActionYellow = Color(0xFFFFD60A)

/** The tools the dock opens a panel for, in dock order (per owner reference). */
enum class EditorTool(val icon: ImageVector, val labelRes: Int) {
  Media(Icons.Outlined.Image, R.string.editor_tool_media),
  Stickers(Icons.Outlined.EmojiEmotions, R.string.editor_tool_stickers),
  Text(Icons.Outlined.TextFields, R.string.editor_tool_text),
  DrawText(Icons.Outlined.EditNote, R.string.editor_tool_draw_text),
  Draw(Icons.Outlined.Draw, R.string.editor_tool_draw),
  Background(Icons.Outlined.FormatColorReset, R.string.editor_tool_background),
  Music(Icons.Outlined.MusicNote, R.string.editor_tool_music),
  Layers(Icons.Outlined.Layers, R.string.editor_tool_layers),
  Timeline(Icons.Outlined.ViewTimeline, R.string.editor_tool_timeline),
  Format(Icons.Outlined.AspectRatio, R.string.editor_tool_format),
}

/**
 * The editor per owner reference: a blank canvas first; media, text, stickers
 * and drawing land on the timeline as clips, like a real video editor.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(format: String, initialMedia: List<StartMedia> = emptyList(), onBack: () -> Unit) {
  val viewModel: EditorViewModel = viewModel(key = format) { EditorViewModel(format) }
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  var activeTool by rememberSaveable { mutableStateOf<EditorTool?>(null) }
  var timelineOpen by rememberSaveable { mutableStateOf(false) }
  // The Timeline tool toggles the timeline panel; the rest open their panels.
  val handleTool: (EditorTool) -> Unit = { tool ->
    if (tool == EditorTool.Timeline) {
      activeTool = null
      timelineOpen = !timelineOpen
    } else {
      activeTool = tool
    }
  }
  // Media picked on the create screen lands on the main track when it opens.
  LaunchedEffect(Unit) {
    if (initialMedia.isNotEmpty()) viewModel.addMediaBatch(initialMedia)
  }

  Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    EditorTopBar(
      canUndo = state.timeline.canUndo,
      canRedo = state.timeline.canRedo,
      onBack = onBack,
      onUndo = viewModel::undo,
      onRedo = viewModel::redo,
    )
    Box(
      modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
      contentAlignment = Alignment.Center,
    ) {
      EditorCanvas(state, onSelect = viewModel::select, onAddMedia = { activeTool = EditorTool.Media })
    }
    if (timelineOpen) {
      // Timeline view: the transport (with Done) sits above the panel and the
      // tool dock steps aside; Done returns to the normal editor.
      TransportRow(onDone = { timelineOpen = false })
      EditorTimeline(
        state,
        viewModel,
        onAddText = { activeTool = EditorTool.Text },
        onAddMedia = { activeTool = EditorTool.Media },
        onAddSticker = { activeTool = EditorTool.Stickers },
      )
    } else {
      EditorDock(onToolSelected = handleTool)
    }
  }

  activeTool?.let { tool ->
    ModalBottomSheet(
      onDismissRequest = { activeTool = null },
      containerColor = MaterialTheme.colorScheme.surface,
      sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
      shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
      when (tool) {
        EditorTool.Media ->
          MediaPanel { path, isVideo, durationMs ->
            viewModel.apply(buildAddMedia(state.timeline, path, isVideo, durationMs))
            activeTool = null
          }
        EditorTool.Text ->
          TextPanel { content ->
            viewModel.apply(
              EditorCommand.AddText(content = content, at_us = 0, duration_us = state.timeline.durationUs),
            )
            activeTool = null
          }
        EditorTool.Stickers ->
          StickerPanel { glyph ->
            viewModel.apply(
              EditorCommand.AddSticker(
                glyph = glyph,
                at_us = 0,
                duration_us = minOf(5_000_000L, state.timeline.durationUs),
              ),
            )
            activeTool = null
          }
        else -> ComingSoonPanel()
      }
    }
  }
}

/** Appends media after the last main-track clip; the engine clamps the span. */
private fun buildAddMedia(
  timeline: EditorTimeline,
  path: String,
  isVideo: Boolean,
  durationMs: Long,
): EditorCommand.AddMedia {
  val startUs = timeline.clips.filter { it.lane == "main" }.maxOfOrNull { it.endUs } ?: 0L
  val durationUs = if (isVideo && durationMs > 0) durationMs else 5_000_000L
  return EditorCommand.AddMedia(path = path, is_video = isVideo, start_us = startUs, duration_us = durationUs)
}

@Composable
private fun EditorTopBar(
  canUndo: Boolean,
  canRedo: Boolean,
  onBack: () -> Unit,
  onUndo: () -> Unit,
  onRedo: () -> Unit,
) {
  val onSurface = MaterialTheme.colorScheme.onSurface
  Row(
    modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 6.dp, vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    IconButton(onClick = onBack) {
      Icon(
        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
        contentDescription = stringResource(R.string.editor_back),
        tint = onSurface,
      )
    }
    Spacer(Modifier.weight(1f))
    IconButton(onClick = onUndo, enabled = canUndo) {
      Icon(
        imageVector = Icons.AutoMirrored.Outlined.Undo,
        contentDescription = stringResource(R.string.editor_undo),
        tint = if (canUndo) onSurface else onSurface.copy(alpha = 0.35f),
      )
    }
    IconButton(onClick = onRedo, enabled = canRedo) {
      Icon(
        imageVector = Icons.AutoMirrored.Outlined.Redo,
        contentDescription = stringResource(R.string.editor_redo),
        tint = if (canRedo) onSurface else onSurface.copy(alpha = 0.35f),
      )
    }
    IconButton(onClick = {}) {
      Icon(
        imageVector = Icons.Outlined.Visibility,
        contentDescription = stringResource(R.string.editor_preview),
        tint = onSurface,
      )
    }
    Text(
      text = stringResource(R.string.editor_share),
      color = Color(0xFF141416),
      fontSize = 16.sp,
      fontWeight = FontWeight.Bold,
      modifier =
        Modifier.padding(start = 4.dp, end = 12.dp)
          .background(ActionYellow, RoundedCornerShape(50))
          .clickable {}
          .padding(horizontal = 18.dp, vertical = 10.dp),
    )
  }
}

/** The preview: blank white first; clips render at their normalized positions. */
@Composable
private fun EditorCanvas(state: EditorUiState, onSelect: (Long?) -> Unit, onAddMedia: () -> Unit) {
  BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    val boxWidth = maxWidth
    val boxHeight = maxHeight
    val ratio = state.timeline.ratio
    val canvasWidth = minOf(boxWidth, boxHeight * ratio)
    val canvasHeight = canvasWidth / ratio
    Box(
      modifier =
        Modifier.width(canvasWidth)
          .height(canvasHeight)
          .clip(RoundedCornerShape(18.dp))
          .background(Color.White)
          .clickable { onSelect(null) },
      contentAlignment = Alignment.Center,
    ) {
      val mediaClip = state.timeline.clips.firstOrNull { it.kind == "media" }
      if (mediaClip != null) {
        AsyncImage(
          model = mediaClip.mediaPath,
          contentDescription = null,
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize(),
        )
      }
      state.timeline.clips.forEach { clip ->
        val selected = clip.id == state.selectedClipId
        val selectionColor = if (mediaClip != null) Color.White else Color(0xFF141416)
        val selection =
          if (selected) Modifier.border(1.5.dp, selectionColor, RoundedCornerShape(8.dp)) else Modifier
        val placement =
          Modifier.align(Alignment.Center)
            .offset(
              x = boxWidth * ((clip.x ?: 0.5f) - 0.5f) * 0.9f,
              y = boxHeight * ((clip.y ?: 0.5f) - 0.5f) * 0.9f,
            )
        when (clip.kind) {
          "text" ->
            Text(
              text = clip.text.orEmpty(),
              color = if (mediaClip != null) Color.White else Color(0xFF141416),
              fontSize = 22.sp,
              fontWeight = FontWeight.Bold,
              textAlign = TextAlign.Center,
              modifier = placement.clickable { onSelect(clip.id) }.then(selection),
            )
          "sticker" ->
            Text(
              text = clip.glyph.orEmpty(),
              fontSize = 44.sp,
              modifier = placement.clickable { onSelect(clip.id) }.then(selection),
            )
        }
      }
      if (state.timeline.clips.isEmpty()) {
        Icon(
          imageVector = Icons.Outlined.Add,
          contentDescription = stringResource(R.string.editor_canvas_add),
          tint = Color(0xFF9A9A9E),
          modifier = Modifier.size(30.dp).clickable { onAddMedia() },
        )
      }
    }
  }
}

/** Time and play controls under the canvas; Done closes the timeline panel. */
@Composable
private fun TransportRow(onDone: () -> Unit) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(text = "00:00", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
    Spacer(Modifier.weight(1f))
    IconButton(onClick = {}) {
      Icon(
        imageVector = Icons.Outlined.PlayArrow,
        contentDescription = stringResource(R.string.editor_play),
        tint = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.size(30.dp),
      )
    }
    Spacer(Modifier.weight(1f))
    Text(
      text = stringResource(R.string.editor_done),
      color = MaterialTheme.colorScheme.onSurface,
      fontSize = 17.sp,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.clickable { onDone() },
    )
  }
}

/** The tool dock: one scrollable row of every tool (per owner reference). */
@Composable
private fun EditorDock(onToolSelected: (EditorTool) -> Unit) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .navigationBarsPadding()
        .padding(top = 8.dp, bottom = 6.dp)
        .horizontalScroll(rememberScrollState()),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    EditorTool.entries.forEach { tool ->
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onToolSelected(tool) }.padding(horizontal = 13.dp),
      ) {
        Icon(
          imageVector = tool.icon,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.height(5.dp))
        Text(
          text = stringResource(tool.labelRes),
          fontSize = 10.sp,
          color = MaterialTheme.colorScheme.onSurface,
        )
      }
    }
  }
}
