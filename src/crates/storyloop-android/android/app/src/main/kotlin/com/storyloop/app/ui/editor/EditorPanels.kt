package com.storyloop.app.ui.editor

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import com.storyloop.app.data.AndroidMediaRepository
import com.storyloop.app.data.RecentMedia
import com.storyloop.app.data.mediaPermissions
import kotlinx.coroutines.launch

/** The action yellow from the reference screenshots. */
private val ActionYellow = Color(0xFFFFD60A)

/** Device media to add to the main track, three to a row. */
@Composable
internal fun MediaPanel(onPick: (path: String, isVideo: Boolean, durationMs: Long) -> Unit) {
  val context = LocalContext.current
  var media by remember { mutableStateOf<List<RecentMedia>>(emptyList()) }
  val scope = rememberCoroutineScope()
  val repository = remember { AndroidMediaRepository(context.contentResolver) }

  val load: () -> Unit = { scope.launch { media = repository.recents(limit = 24) } }
  val launcher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
      load()
    }
  LaunchedEffect(Unit) {
    val missing =
      mediaPermissions().filter {
        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
      }
    if (missing.isEmpty()) load() else launcher.launch(missing.toTypedArray())
  }

  Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
    Text(
      text = stringResource(R.string.editor_media_title),
      fontSize = 18.sp,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
    if (media.isEmpty()) {
      Text(
        text = stringResource(R.string.editor_media_empty),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
      )
    } else {
      LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxWidth().height(380.dp).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        items(media) { item ->
          AsyncImage(
            model = item.uri,
            contentDescription = stringResource(R.string.cd_recent_media),
            contentScale = ContentScale.Crop,
            modifier =
              Modifier.aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .clickable { onPick(item.uri.toString(), item.isVideo, item.durationMs ?: 0L) },
          )
        }
      }
    }
  }
}

/** Type a caption; it lands on the timeline's text row. */
@Composable
internal fun TextPanel(onAdd: (String) -> Unit) {
  var text by rememberSaveable { mutableStateOf("") }
  Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
    TextField(
      value = text,
      onValueChange = { text = it },
      placeholder = { Text(stringResource(R.string.editor_text_hint)) },
      shape = RoundedCornerShape(14.dp),
      colors =
        TextFieldDefaults.colors(
          focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
          unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
          focusedIndicatorColor = Color.Transparent,
          unfocusedIndicatorColor = Color.Transparent,
          cursorColor = MaterialTheme.colorScheme.primary,
        ),
      modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))
    Button(
      onClick = { onAdd(text.trim()) },
      enabled = text.isNotBlank(),
      shape = RoundedCornerShape(50),
      colors =
        ButtonDefaults.buttonColors(
          containerColor = ActionYellow,
          contentColor = Color(0xFF141416),
          disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
          disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Text(text = stringResource(R.string.editor_add_text), fontWeight = FontWeight.Bold)
    }
    Spacer(Modifier.height(24.dp))
  }
}

/** Emoji stickers, three to a row. */
@Composable
internal fun StickerPanel(onPick: (String) -> Unit) {
  val stickers =
    listOf(
      "🔥", "😂", "❤️", "✨", "🎉", "😍", "😎", "🙌", "💯", "👀", "🥹", "😭",
      "✅", "⭐️", "🎵", "💥", "🤍", "🙏", "🤩", "🫶", "⚡️", "🌈", "🍕", "🐶",
    )
  Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
    Text(
      text = stringResource(R.string.editor_stickers_title),
      fontSize = 18.sp,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
    LazyVerticalGrid(
      columns = GridCells.Fixed(4),
      modifier = Modifier.fillMaxWidth().height(360.dp).padding(horizontal = 12.dp),
    ) {
      items(stickers) { glyph ->
        Text(
          text = glyph,
          fontSize = 40.sp,
          modifier =
            Modifier.aspectRatio(1f)
              .clickable { onPick(glyph) }
              .padding(10.dp),
        )
      }
    }
  }
}

/** A placeholder for tools arriving with the next milestone. */
@Composable
internal fun ComingSoonPanel() {
  Column(
    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
      text = stringResource(R.string.editor_coming_soon),
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      fontSize = 14.sp,
    )
  }
}
