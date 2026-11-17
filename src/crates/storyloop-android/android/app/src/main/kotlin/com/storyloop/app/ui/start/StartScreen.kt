package com.storyloop.app.ui.start

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.storyloop.app.R
import com.storyloop.app.StartMedia
import com.storyloop.app.data.AndroidMediaRepository
import com.storyloop.app.data.RecentMedia
import com.storyloop.app.data.SampleTemplateRepository
import com.storyloop.app.data.Template
import com.storyloop.app.data.TemplateSection
import com.storyloop.app.data.mediaPermissions
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** The action yellow from the reference screenshots. */
private val StartYellow = Color(0xFFFFD60A)

private const val RECENTS_LIMIT = 60

/** The chips across the top; picking one swaps the template row. */
private enum class StartChip(val labelRes: Int) {
  Trending(R.string.start_chip_trending),
  Halloween(R.string.start_chip_halloween),
  Beige(R.string.start_chip_beige),
  PhotoGallery(R.string.start_chip_photo_gallery),
}

/** Content tabs above the media grid. */
private enum class StartTab(val labelRes: Int) {
  Library(R.string.start_tab_library),
  StockPhotos(R.string.start_tab_stock_photos),
  StockVideos(R.string.start_tab_stock_videos),
}

/**
 * The create screen the + button opens, per owner reference: template cards up
 * top (a blank card starts from scratch), the device's recents below with
 * multi-select circles, and Create takes the picks into the editor.
 */
@Composable
fun StartScreen(onClose: () -> Unit, onOpenEditor: (List<StartMedia>) -> Unit) {
  val context = LocalContext.current
  var chip by rememberSaveable { mutableStateOf(StartChip.Trending) }
  var tab by rememberSaveable { mutableStateOf(StartTab.Library) }
  var templates by remember { mutableStateOf<List<Template>>(emptyList()) }
  var media by remember { mutableStateOf<List<RecentMedia>>(emptyList()) }
  var selection by remember { mutableStateOf<List<StartMedia>>(emptyList()) }

  val scope = rememberCoroutineScope()
  val repository = remember { AndroidMediaRepository(context.contentResolver) }
  val loadRecents: () -> Unit = { scope.launch { media = repository.recents(limit = RECENTS_LIMIT) } }
  val launcher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
      loadRecents()
    }
  LaunchedEffect(Unit) {
    templates = SampleTemplateRepository().templates.first()
    val missing =
      mediaPermissions().filter {
        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
      }
    if (missing.isEmpty()) loadRecents() else launcher.launch(missing.toTypedArray())
  }

  // Placeholder template sets until the real template system arrives: Trending
  // and Photo Gallery show the trending covers, the look chips the filters.
  val templateRow =
    if (chip == StartChip.Trending || chip == StartChip.PhotoGallery) {
      templates.filter { it.section == TemplateSection.Trending }
    } else {
      templates.filter { it.section == TemplateSection.Filters }
    }

  Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
      IconButton(onClick = onClose, modifier = Modifier.padding(start = 8.dp)) {
        Icon(
          imageVector = Icons.Outlined.Close,
          contentDescription = stringResource(R.string.start_close),
          tint = MaterialTheme.colorScheme.onSurface,
        )
      }
      LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(top = 4.dp),
      ) {
        items(StartChip.entries) { entry ->
          val selected = entry == chip
          Text(
            text = stringResource(entry.labelRes),
            fontSize = 16.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color =
              if (selected) Color(0xFF141416)
              else MaterialTheme.colorScheme.onSurface,
            modifier =
              Modifier.clip(RoundedCornerShape(50))
                .background(if (selected) StartYellow else Color.Transparent)
                .clickable { chip = entry }
                .padding(horizontal = 16.dp, vertical = 10.dp),
          )
        }
      }
      LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
      ) {
        item {
          Box(
            modifier =
              Modifier.width(170.dp)
                .aspectRatio(9f / 16f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable { onOpenEditor(emptyList()) },
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.Outlined.Add,
              contentDescription = stringResource(R.string.start_blank_scratch),
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(34.dp),
            )
          }
        }
        items(templateRow, key = { it.id }) { template ->
          Box(
            modifier =
              Modifier.width(170.dp)
                .aspectRatio(9f / 16f)
                .clip(RoundedCornerShape(16.dp))
                .background(templateBrush(template))
                .clickable { onOpenEditor(emptyList()) },
          ) {
            Icon(
              imageVector = Icons.Outlined.FavoriteBorder,
              contentDescription = stringResource(R.string.cd_favorite_template),
              tint = Color.White,
              modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).size(18.dp),
            )
            if (template.isPro) {
              Text(
                text = stringResource(R.string.pro_badge),
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier =
                  Modifier.align(Alignment.BottomEnd)
                    .padding(10.dp)
                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(50))
                    .padding(horizontal = 9.dp, vertical = 4.dp),
              )
            }
          }
        }
      }
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
      ) {
        StartTab.entries.forEach { entry ->
          val selected = entry == tab
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
              text = stringResource(entry.labelRes),
              fontSize = 17.sp,
              fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
              color = if (selected) StartYellow else MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.clickable { tab = entry },
            )
            Box(
              modifier =
                Modifier.padding(top = 4.dp)
                  .width(72.dp)
                  .height(2.dp)
                  .background(if (selected) StartYellow else Color.Transparent),
            )
          }
        }
      }
      when (tab) {
        StartTab.Library -> {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = stringResource(R.string.start_reset),
              fontSize = 15.sp,
              color =
                if (selection.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.clickable(enabled = selection.isNotEmpty()) { selection = emptyList() },
            )
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                text = stringResource(R.string.start_recents),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
              )
              Icon(
                imageVector = Icons.Outlined.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp),
              )
            }
            Spacer(Modifier.weight(1f))
          }
          if (media.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
              Text(
                text = stringResource(R.string.editor_media_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          } else {
            LazyVerticalGrid(
              columns = GridCells.Fixed(3),
              modifier = Modifier.fillMaxWidth().weight(1f),
              horizontalArrangement = Arrangement.spacedBy(2.dp),
              verticalArrangement = Arrangement.spacedBy(2.dp),
              contentPadding = PaddingValues(bottom = 96.dp),
            ) {
              items(media, key = { it.uri }) { item ->
                val itemMedia = StartMedia(item.uri.toString(), item.isVideo, item.durationMs ?: 0L)
                val picked = itemMedia in selection
                Box(
                  modifier =
                    Modifier.aspectRatio(0.9f)
                      .clickable {
                        selection = if (picked) selection - itemMedia else selection + itemMedia
                      },
                ) {
                  AsyncImage(
                    model = item.uri,
                    contentDescription = stringResource(R.string.cd_recent_media),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                  )
                  Box(
                    modifier =
                      Modifier.align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .then(
                          if (picked) {
                            Modifier.background(StartYellow)
                          } else {
                            Modifier.background(Color.Black.copy(alpha = 0.25f))
                              .border(1.5.dp, Color.White, CircleShape)
                          },
                        ),
                    contentAlignment = Alignment.Center,
                  ) {
                    if (picked) {
                      Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        tint = Color(0xFF141416),
                        modifier = Modifier.size(14.dp),
                      )
                    }
                  }
                }
              }
            }
          }
        }
        StartTab.StockPhotos, StartTab.StockVideos -> {
          Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Text(
              text = stringResource(R.string.start_stock_empty),
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }
    // Create: appears once media is picked; carries the picks into the editor.
    AnimatedVisibility(
      visible = selection.isNotEmpty(),
      modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 24.dp),
      enter = fadeIn() + slideInVertically { it / 2 },
      exit = fadeOut() + slideOutVertically { it / 2 },
    ) {
      Text(
        text = stringResource(R.string.start_create_count, selection.size),
        color = Color(0xFF141416),
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        modifier =
          Modifier.background(StartYellow, RoundedCornerShape(50))
            .clickable { onOpenEditor(selection) }
            .padding(horizontal = 30.dp, vertical = 14.dp),
      )
    }
  }
}

/** Gradient placeholder art; real template covers replace this later. */
private fun templateBrush(template: Template): Brush =
  if (template.gradient.isEmpty()) {
    Brush.verticalGradient(listOf(Color(0xFF1C1C20), Color(0xFF2C2C34)))
  } else {
    Brush.verticalGradient(template.gradient)
  }
