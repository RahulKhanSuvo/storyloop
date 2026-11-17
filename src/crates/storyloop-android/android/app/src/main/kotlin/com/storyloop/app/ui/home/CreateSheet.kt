package com.storyloop.app.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.CropPortrait
import androidx.compose.material.icons.outlined.CropSquare
import androidx.compose.material.icons.outlined.DataUsage
import androidx.compose.material.icons.outlined.Feed
import androidx.compose.material.icons.outlined.Landscape
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.OndemandVideo
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.storyloop.app.R
import com.storyloop.app.data.AndroidMediaRepository
import com.storyloop.app.data.RecentMedia
import kotlinx.coroutines.launch

/** A social format the user can start a new video in; card heights vary per reference. */
private data class CreateFormat(val labelRes: Int, val icon: ImageVector, val cardHeight: Int)

private val CREATE_FORMATS = listOf(
  CreateFormat(R.string.format_reel, Icons.Outlined.Movie, 105),
  CreateFormat(R.string.format_story, Icons.Outlined.DataUsage, 105),
  CreateFormat(R.string.format_tiktok, Icons.Outlined.MusicNote, 105),
  CreateFormat(R.string.format_post, Icons.Outlined.Photo, 90),
  CreateFormat(R.string.format_square, Icons.Outlined.CropSquare, 75),
  CreateFormat(R.string.format_video, Icons.Outlined.Videocam, 90),
  CreateFormat(R.string.format_landscape, Icons.Outlined.Landscape, 60),
  CreateFormat(R.string.format_youtube, Icons.Outlined.SmartDisplay, 55),
  CreateFormat(R.string.format_shorts, Icons.Outlined.CropPortrait, 120),
  CreateFormat(R.string.format_ad, Icons.Outlined.Campaign, 100),
  CreateFormat(R.string.format_fb_post, Icons.Outlined.Feed, 55),
  CreateFormat(R.string.format_x_video, Icons.Outlined.OndemandVideo, 90),
  CreateFormat(R.string.format_tweet, Icons.Outlined.AlternateEmail, 55),
  CreateFormat(R.string.format_pin, Icons.Outlined.PushPin, 100),
  CreateFormat(R.string.format_presentations, Icons.Outlined.Slideshow, 100),
)

/** Fallback art when no media permission / no media yet; real media replaces these. */
private val RECENT_ART =
  listOf(
    listOf(Color(0xFF0B0E14), Color(0xFF2B3A55)),
    listOf(Color(0xFF26170F), Color(0xFF6B3A1F)),
    listOf(Color(0xFF4A2C3A), Color(0xFFC98BA6)),
  )

/** Either a real device media item or placeholder gradient art. */
private sealed interface MediaThumb {
  data class Real(val media: RecentMedia) : MediaThumb

  data class Placeholder(val gradient: List<Color>) : MediaThumb
}

private const val RECENTS_LIMIT = 8

/** Media permissions by OS level: 34+ also supports user-selected partial access. */
private fun mediaPermissions(): List<String> =
  if (Build.VERSION.SDK_INT >= 33) {
    buildList {
      add(Manifest.permission.READ_MEDIA_IMAGES)
      add(Manifest.permission.READ_MEDIA_VIDEO)
      if (Build.VERSION.SDK_INT >= 34) {
        add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
      }
    }
  } else {
    listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
  }

/**
 * Create bottom sheet per owner reference: pick a format, then media
 * (camera, gallery, then the user's recent captures). Deliberately has no
 * "Start with subtitle" section (owner request).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateSheet(onDismiss: () -> Unit) {
  val context = LocalContext.current
  var recentMedia by remember { mutableStateOf<List<RecentMedia>>(emptyList()) }
  val scope = rememberCoroutineScope()
  val mediaRepository = remember { AndroidMediaRepository(context.contentResolver) }

  val loadRecents: () -> Unit = {
    scope.launch { recentMedia = mediaRepository.recents(limit = RECENTS_LIMIT) }
  }
  val permissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
      // Granted or denied — load whatever MediaStore exposes; empty shows placeholders.
      loadRecents()
    }
  LaunchedEffect(Unit) {
    val perms = mediaPermissions()
    val missing = perms.filter {
      ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
    }
    // Full media access already granted → don't ask for partial selection again.
    val fullAccessMissing =
      missing.any {
        it == Manifest.permission.READ_MEDIA_IMAGES || it == Manifest.permission.READ_MEDIA_VIDEO
      }
    val request = if (fullAccessMissing) missing else missing - Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
    Log.i("CreateSheet", "media permissions: have=${perms - missing.toSet()} requesting=$request")
    if (request.isEmpty()) loadRecents() else permissionLauncher.launch(request.toTypedArray())
  }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    containerColor = MaterialTheme.colorScheme.surface,
    sheetState = rememberModalBottomSheetState(),
    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
      Text(
        text = stringResource(R.string.create_sheet_format_title),
        fontSize = 21.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = 20.dp, bottom = 16.dp),
      )
      LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Bottom,
      ) {
        items(CREATE_FORMATS) { format ->
          FormatCard(format)
        }
      }
      Text(
        text = stringResource(R.string.create_sheet_media_title),
        fontSize = 21.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = 20.dp, top = 28.dp, bottom = 16.dp),
      )
      LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        item {
          MediaCard(
            icon = Icons.Outlined.PhotoCamera,
            description = stringResource(R.string.media_camera),
          )
        }
        item {
          MediaCard(
            icon = Icons.Outlined.AddPhotoAlternate,
            description = stringResource(R.string.media_gallery),
          )
        }
        // Computed outside the lazy DSL so the row rebuilds when media loads.
        val mediaThumbs: List<MediaThumb> =
          if (recentMedia.isEmpty()) {
            RECENT_ART.map { MediaThumb.Placeholder(it) }
          } else {
            recentMedia.map { MediaThumb.Real(it) }
          }
        items(mediaThumbs) { thumb ->
          when (thumb) {
            is MediaThumb.Placeholder ->
              Box(
                modifier =
                  Modifier.size(120.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Brush.verticalGradient(thumb.gradient)),
              )
            is MediaThumb.Real -> RecentThumb(thumb.media)
          }
        }
      }
    }
  }
}

@Composable
private fun FormatCard(format: CreateFormat) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Box(
      modifier =
        Modifier.size(width = 88.dp, height = format.cardHeight.dp)
          .clip(RoundedCornerShape(20.dp))
          .background(MaterialTheme.colorScheme.surfaceContainerHigh),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = format.icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.size(30.dp),
      )
    }
    Spacer(Modifier.height(6.dp))
    Text(
      text = stringResource(format.labelRes),
      fontSize = 13.sp,
      color = MaterialTheme.colorScheme.onSurface,
    )
  }
}

/** Icon-only media card (no label, owner request); names live in contentDescription. */
@Composable
private fun MediaCard(icon: ImageVector, description: String) {
  Box {
    Box(
      modifier =
        Modifier.size(120.dp)
          .clip(RoundedCornerShape(24.dp))
          .background(MaterialTheme.colorScheme.surfaceContainerHigh),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = description,
        tint = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.size(34.dp),
      )
    }
  }
}

/** Recent photo/video thumbnail; videos get a duration chip. */
@Composable
private fun RecentThumb(media: RecentMedia) {
  Box(
    modifier =
      Modifier.size(120.dp)
        .clip(RoundedCornerShape(24.dp))
        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
  ) {
    AsyncImage(
      model = media.uri,
      contentDescription = stringResource(R.string.cd_recent_media),
      contentScale = ContentScale.Crop,
      modifier = Modifier.fillMaxSize(),
    )
    if (media.isVideo && media.durationMs != null && media.durationMs > 0) {
      Text(
        text = formatDuration(media.durationMs),
        color = Color.White,
        fontSize = 10.sp,
        modifier =
          Modifier.align(Alignment.BottomStart)
            .padding(6.dp)
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50))
            .padding(horizontal = 6.dp, vertical = 2.dp),
      )
    }
  }
}

private fun formatDuration(ms: Long): String {
  val totalSeconds = ms / 1000
  return "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"
}
