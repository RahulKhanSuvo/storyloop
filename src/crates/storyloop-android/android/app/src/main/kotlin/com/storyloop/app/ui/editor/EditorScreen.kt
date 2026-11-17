package com.storyloop.app.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import com.storyloop.app.core.EditorCommand
import com.storyloop.app.core.EditorTimeline

/** The action yellow from the reference screenshots. */
private val ActionYellow = Color(0xFFFFD60A)

/** The tools the dock opens a panel for. */
enum class EditorTool { Media, Text, Stickers, Draw, Music }

/**
 * The editor per owner reference: a blank canvas first; media, text, stickers
 * and drawing land on the timeline as clips, like a real video editor.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(format: String, onBack: () -> Unit) {
  val viewModel: EditorViewModel = viewModel(key = format) { EditorViewModel(format) }
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  var activeTool by rememberSaveable { mutableStateOf<EditorTool?>(null) }

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
      EditorCanvas(state, onSelect = viewModel::select)
    }
    TransportRow()
    EditorTimeline(state, viewModel)
    EditorDock(onToolSelected = { tool -> activeTool = tool })
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
        EditorTool.Draw -> ComingSoonPanel()
        EditorTool.Music -> ComingSoonPanel()
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
private fun EditorCanvas(state: EditorUiState, onSelect: (Long?) -> Unit) {
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
          contentDescription = stringResource(R.string.editor_canvas_hint),
          tint = Color(0xFF9A9A9E),
          modifier = Modifier.size(30.dp),
        )
      }
    }
  }
}

/** Time and play controls under the canvas. */
@Composable
private fun TransportRow() {
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
    )
  }
}

@Composable
private fun EditorDock(onToolSelected: (EditorTool) -> Unit) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f))
        .navigationBarsPadding()
        .padding(vertical = 10.dp),
    horizontalArrangement = Arrangement.SpaceEvenly,
  ) {
    DockTool(Icons.Outlined.Image, stringResource(R.string.editor_tool_media), EditorTool.Media, onToolSelected)
    DockTool(Icons.Outlined.TextFields, stringResource(R.string.editor_tool_text), EditorTool.Text, onToolSelected)
    DockTool(Icons.Outlined.EmojiEmotions, stringResource(R.string.editor_tool_stickers), EditorTool.Stickers, onToolSelected)
    DockTool(Icons.Outlined.Draw, stringResource(R.string.editor_tool_draw), EditorTool.Draw, onToolSelected)
    DockTool(Icons.Outlined.MusicNote, stringResource(R.string.editor_tool_music), EditorTool.Music, onToolSelected)
  }
}

@Composable
private fun DockTool(icon: ImageVector, label: String, tool: EditorTool, onToolSelected: (EditorTool) -> Unit) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier.clickable { onToolSelected(tool) }.padding(horizontal = 12.dp),
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.size(22.dp),
    )
    Spacer(Modifier.height(4.dp))
    Text(text = label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
  }
}
