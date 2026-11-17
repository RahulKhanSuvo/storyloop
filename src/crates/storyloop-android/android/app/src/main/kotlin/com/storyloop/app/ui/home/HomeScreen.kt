package com.storyloop.app.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.storyloop.app.R
import com.storyloop.app.data.SampleTemplateRepository
import com.storyloop.app.data.Template
import com.storyloop.app.data.TemplateCategory
import com.storyloop.app.ui.theme.StoryLoopTheme

/** Home / template library screen — the app's entry point. */
@Composable
fun HomeScreen(
  modifier: Modifier = Modifier,
  onOpenEditor: (String) -> Unit = {},
  viewModel: HomeViewModel = viewModel { HomeViewModel(SampleTemplateRepository()) },
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  when (val state = uiState) {
    HomeUiState.Loading -> {
      Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
      }
    }
    is HomeUiState.Error -> {
      Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.error_loading_data, state.throwable.message ?: ""))
      }
    }
    is HomeUiState.Success -> {
      HomeContent(
        state = state,
        onCategorySelected = viewModel::selectCategory,
        onOpenEditor = onOpenEditor,
        modifier = modifier,
      )
    }
  }
}

@Composable
private fun HomeContent(
  state: HomeUiState.Success,
  onCategorySelected: (TemplateCategory) -> Unit,
  onOpenEditor: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  var showCreateSheet by rememberSaveable { mutableStateOf(false) }
  Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    Column(
      modifier =
        Modifier.fillMaxSize()
          .verticalScroll(rememberScrollState())
          .padding(bottom = 150.dp)
    ) {
      TopBarRow(
        selectedCategory = state.selectedCategory,
        onCategorySelected = onCategorySelected,
      )
      state.featured?.let { FeaturedCard(it, onCreateClick = { showCreateSheet = true }) }
      SectionHeader(title = stringResource(R.string.home_trending))
      TemplateRow(templates = state.trending, showLabel = false)
      SectionHeader(title = stringResource(R.string.home_filters))
      Text(
        text = stringResource(R.string.home_filters_subtitle),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp),
      )
      Spacer(Modifier.height(12.dp))
      TemplateRow(templates = state.filters, showLabel = true)
    }
    FloatingDock(
      onCreateClick = { showCreateSheet = true },
      modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
    )
  }
  if (showCreateSheet) {
    CreateSheet(onDismiss = { showCreateSheet = false }, onFormatSelected = onOpenEditor)
  }
}

@Composable
private fun TopBarRow(
  selectedCategory: TemplateCategory,
  onCategorySelected: (TemplateCategory) -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier.fillMaxWidth().statusBarsPadding().padding(top = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    CategoryTabs(
      selected = selectedCategory,
      onSelected = onCategorySelected,
      modifier = Modifier.weight(1f).padding(start = 16.dp, end = 16.dp),
    )
  }
}

@Composable
private fun CategoryTabs(
  selected: TemplateCategory,
  onSelected: (TemplateCategory) -> Unit,
  modifier: Modifier = Modifier,
) {
  LazyRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
    items(TemplateCategory.entries) { category ->
      val isSelected = category == selected
      Text(
        text = category.label(),
        fontSize = if (isSelected) 19.sp else 17.sp,
        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
        color =
          if (isSelected) MaterialTheme.colorScheme.onSurface
          else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.clickable { onSelected(category) },
      )
    }
  }
}

@Composable
private fun TemplateCategory.label(): String =
  when (this) {
    TemplateCategory.ForYou -> stringResource(R.string.tab_for_you)
    TemplateCategory.Lifestyle -> stringResource(R.string.tab_lifestyle)
    TemplateCategory.Business -> stringResource(R.string.tab_business)
    TemplateCategory.Collage -> stringResource(R.string.tab_collage)
  }

@Composable
private fun FeaturedCard(
  template: Template,
  onCreateClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier =
      modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 12.dp)
        .aspectRatio(0.92f) // showcase crop — the template itself is 9:16
        .clip(RoundedCornerShape(28.dp))
        .background(templateArtBrush(template))
  ) {
    Column(
      modifier = Modifier.align(Alignment.Center),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(
        text = template.title,
        color = Color.White,
        fontSize = 38.sp,
        lineHeight = 46.sp,
        fontWeight = FontWeight.Medium,
        fontFamily = FontFamily.Serif,
        fontStyle = FontStyle.Italic,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 24.dp),
      )
      Spacer(Modifier.height(22.dp))
      Button(
        onClick = onCreateClick,
        shape = RoundedCornerShape(50),
        colors =
          ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
          ),
      ) {
        Text(
          text = stringResource(R.string.home_create_reels),
          fontWeight = FontWeight.Bold,
          fontSize = 17.sp,
          modifier = Modifier.padding(horizontal = 12.dp),
        )
      }
    }
  }
}

@Composable
private fun SectionHeader(title: String, modifier: Modifier = Modifier) {
  Row(
    modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = title,
      style = MaterialTheme.typography.titleLarge,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.onSurface,
    )
    Spacer(Modifier.weight(1f))
    Icon(
      imageVector = Icons.Outlined.ChevronRight,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun TemplateRow(templates: List<Template>, showLabel: Boolean) {
  LazyRow(
    contentPadding = PaddingValues(horizontal = 16.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    items(templates, key = { it.id }) { template ->
      TemplateCard(template = template, showLabel = showLabel)
    }
  }
}

@Composable
private fun TemplateCard(template: Template, showLabel: Boolean) {
  Box(
    modifier =
      Modifier.width(150.dp)
        .aspectRatio(template.aspectRatio.value)
        .clip(RoundedCornerShape(20.dp))
        .background(templateArtBrush(template))
  ) {
    if (showLabel) {
      // Filter preset card: PRO top-right, name bottom-center
      ProChip(modifier = Modifier.align(Alignment.TopEnd).padding(10.dp))
      Text(
        text = template.title,
        color = Color.White,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
      )
    } else {
      // Trending card: heart top-right, media count bottom-left, PRO bottom-right
      Icon(
        imageVector = Icons.Outlined.FavoriteBorder,
        contentDescription = stringResource(R.string.cd_favorite_template),
        tint = Color.White,
        modifier =
          Modifier.align(Alignment.TopEnd)
            .padding(10.dp)
            .background(Color.Black.copy(alpha = 0.25f), CircleShape)
            .padding(6.dp)
            .size(18.dp),
      )
      Row(
        modifier =
          Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        if (template.mediaCount > 0) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
              Modifier.background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(50))
                .padding(horizontal = 8.dp, vertical = 3.dp),
          ) {
            Icon(
              imageVector = Icons.Outlined.Image,
              contentDescription = null,
              tint = Color.White,
              modifier = Modifier.size(13.dp),
            )
            Spacer(Modifier.width(3.dp))
            Text(text = "${template.mediaCount}", color = Color.White, fontSize = 12.sp)
          }
        } else {
          Spacer(Modifier)
        }
        if (template.isPro) {
          ProChip(modifier = Modifier)
        }
      }
    }
  }
}

/** Gradient placeholder art; real thumbnail media replaces this later. */
@Composable
private fun templateArtBrush(template: Template): Brush =
  if (template.gradient.isEmpty()) {
    SolidColor(MaterialTheme.colorScheme.surfaceContainerHigh)
  } else {
    Brush.verticalGradient(template.gradient)
  }

@Composable
private fun ProChip(modifier: Modifier = Modifier) {
  Text(
    text = stringResource(R.string.pro_badge),
    color = Color.White,
    fontSize = 11.sp,
    fontWeight = FontWeight.Bold,
    modifier =
      modifier
        .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(50))
        .padding(horizontal = 9.dp, vertical = 4.dp),
  )
}

@Composable
private fun FloatingDock(onCreateClick: () -> Unit, modifier: Modifier = Modifier) {
  Surface(
    shape = RoundedCornerShape(50),
    color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.78f),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    shadowElevation = 6.dp,
    modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 38.dp, vertical = 6.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      DockButton(Icons.Outlined.GridView, stringResource(R.string.cd_nav_templates), onClick = {})
      DockButton(Icons.AutoMirrored.Outlined.List, stringResource(R.string.cd_nav_library), onClick = {})
      DockButton(Icons.Outlined.AddCircleOutline, stringResource(R.string.cd_nav_create), onClick = onCreateClick)
      DockButton(Icons.Outlined.Person, stringResource(R.string.cd_nav_profile), onClick = {})
    }
  }
}

@Composable
private fun DockButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
  IconButton(onClick = onClick) {
    Icon(
      imageVector = icon,
      contentDescription = contentDescription,
      tint = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.size(26.dp),
    )
  }
}

@Preview(showBackground = true, showSystemUi = true, name = "Home - dark")
@Composable
private fun HomeScreenDarkPreview() {
  StoryLoopTheme(darkTheme = true) { HomeScreen() }
}

@Preview(showBackground = true, showSystemUi = true, name = "Home - light")
@Composable
private fun HomeScreenLightPreview() {
  StoryLoopTheme(darkTheme = false) { HomeScreen() }
}
