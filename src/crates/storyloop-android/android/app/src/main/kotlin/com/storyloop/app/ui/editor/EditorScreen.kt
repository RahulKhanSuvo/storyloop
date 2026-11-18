package com.storyloop.app.ui.editor

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.outlined.Pause
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
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
  val appContext = LocalContext.current.applicationContext
  StoryThumbs.init(appContext) // the timeline's thumbnails need a context to read media
  val viewModel: EditorViewModel = viewModel(key = format) { EditorViewModel(format, appContext) }
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val playing by viewModel.playing.collectAsStateWithLifecycle()
  val currentTimeUs by viewModel.currentTimeUs.collectAsStateWithLifecycle()
  var activeTool by rememberSaveable { mutableStateOf<EditorTool?>(null) }
  var timelineOpen by rememberSaveable { mutableStateOf(false) }
  val audioPicker =
    rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
      uri ?: return@rememberLauncherForActivityResult
      viewModel.addAudio(uri.toString(), queryDisplayName(appContext, uri), audioDurationUs(appContext, uri))
    }
  // The Timeline tool toggles the timeline panel, Music picks a sound; the rest open their panels.
  val handleTool: (EditorTool) -> Unit = { tool ->
    when {
      tool == EditorTool.Timeline -> {
        activeTool = null
        timelineOpen = !timelineOpen
      }
      tool == EditorTool.Music -> audioPicker.launch("audio/*")
      else -> activeTool = tool
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
      EditorCanvas(
        state,
        currentTimeUs,
        playing,
        onSelect = viewModel::select,
        onAddMedia = { activeTool = EditorTool.Media },
      )
    }
    if (timelineOpen) {
      // Timeline view: the transport (with Done) sits above the panel and the
      // tool dock steps aside; Done returns to the normal editor.
      TransportRow(
        timeUs = currentTimeUs,
        playing = playing,
        onTogglePlay = { if (playing) viewModel.pause() else viewModel.play() },
        onDone = { timelineOpen = false },
      )
      EditorTimeline(
        state,
        viewModel,
        timeUs = currentTimeUs,
        playing = playing,
        modifier = Modifier.fillMaxWidth().weight(1f),
        onAddText = { activeTool = EditorTool.Text },
        onAddMedia = { activeTool = EditorTool.Media },
        onAddSticker = { activeTool = EditorTool.Stickers },
        onAddAudio = { audioPicker.launch("audio/*") },
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

/** What a picked audio file is called, for the clip's label. */
private fun queryDisplayName(context: Context, uri: Uri): String =
  runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
      if (c.moveToFirst()) c.getString(0) else null
    }
  }.getOrNull() ?: uri.lastPathSegment ?: "Audio"

/** How long a picked audio file runs, in microseconds; ten seconds when it cannot be read. */
private fun audioDurationUs(context: Context, uri: Uri): Long {
  val retriever = MediaMetadataRetriever()
  return try {
    retriever.setDataSource(context, uri)
    (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 10_000L) * 1_000
  } catch (e: Exception) {
    10_000_000L
  } finally {
    runCatching { retriever.release() }
  }
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

/** The preview: blank white first; the clip under the playhead fills the canvas. */
@Composable
private fun EditorCanvas(
  state: EditorUiState,
  timeUs: Long,
  playing: Boolean,
  onSelect: (Long?) -> Unit,
  onAddMedia: () -> Unit,
) {
  BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    val boxWidth = maxWidth
    val boxHeight = maxHeight
    val ratio = state.timeline.ratio
    val canvasWidth = minOf(boxWidth, boxHeight * ratio)
    val canvasHeight = canvasWidth / ratio

    // The main-track clip under the playhead; gaps keep showing the previous
    // one, so the preview never flickers blank between clips.
    val mainClips = state.timeline.clips.filter { it.lane == "main" }
    val active =
      mainClips.lastOrNull { timeUs >= it.startUs && timeUs < it.endUs }
        ?: mainClips.lastOrNull { it.startUs <= timeUs }
        ?: mainClips.firstOrNull()
    val activeVideo = active?.takeIf { it.isVideo == true && it.mediaPath != null }

    Box(
      modifier =
        Modifier.width(canvasWidth)
          .height(canvasHeight)
          .clip(RoundedCornerShape(18.dp))
          .background(Color.White)
          .clickable { onSelect(null) },
      contentAlignment = Alignment.Center,
    ) {
      if (active != null) {
        if (activeVideo != null && playing) {
          // Real video playback while playing: one ExoPlayer, driven to the
          // clip's local position; the play clock stays the master.
          VideoSurface(mediaPath = activeVideo.mediaPath.orEmpty(), playing = playing)
        } else {
          AsyncImage(
            model = active.mediaPath,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
          )
        }
      }
      // Overlays only show while they are on the timeline.
      state.timeline.clips
        .filter { it.lane != "main" && timeUs >= it.startUs && timeUs < it.endUs }
        .forEach { clip ->
          val selected = clip.id == state.selectedClipId
          val selectionColor = if (active != null) Color.White else Color(0xFF141416)
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
                color = if (active != null) Color.White else Color(0xFF141416),
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

/** One ExoPlayer rendering the active video into the canvas while playing. */
@OptIn(UnstableApi::class)
@Composable
private fun VideoSurface(mediaPath: String, playing: Boolean) {
  val context = LocalContext.current
  val player = remember { ExoPlayer.Builder(context).build() }
  DisposableEffect(mediaPath) {
    player.setMediaItem(MediaItem.fromUri(mediaPath))
    player.prepare()
    onDispose {
      player.release()
    }
  }
  LaunchedEffect(playing) {
    if (playing) {
      if (player.playbackState == ExoPlayer.STATE_ENDED) player.seekTo(0)
      player.playWhenReady = true
    } else {
      player.playWhenReady = false
    }
  }
  AndroidView(
    modifier = Modifier.fillMaxSize().background(Color.Black),
    factory = { ctx ->
      PlayerView(ctx).apply {
        useController = false
        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        this.player = player
      }
    },
  )
}

/** Time, play/pause and Done controls under the canvas; Done closes the timeline panel. */
@Composable
private fun TransportRow(timeUs: Long, playing: Boolean, onTogglePlay: () -> Unit, onDone: () -> Unit) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = "%02d:%02d".format(timeUs / 60_000_000, (timeUs / 1_000_000) % 60),
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      fontSize = 13.sp,
    )
    Spacer(Modifier.weight(1f))
    IconButton(onClick = onTogglePlay) {
      Icon(
        imageVector = if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
        contentDescription = stringResource(if (playing) R.string.editor_pause else R.string.editor_play),
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
