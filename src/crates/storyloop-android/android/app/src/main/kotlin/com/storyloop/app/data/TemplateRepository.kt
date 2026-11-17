package com.storyloop.app.data

import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Source of the template catalog. Swap for a local DB / remote feed later. */
interface TemplateRepository {
  val templates: Flow<List<Template>>
}

/** Sample catalog standing in for the real template store. */
class SampleTemplateRepository : TemplateRepository {
  override val templates: Flow<List<Template>> = flow { emit(SAMPLE_CATALOG) }
}

private val SAMPLE_CATALOG =
  listOf(
    Template(
      id = "featured-autumn-reels",
      title = "Create Autumn Reels",
      category = TemplateCategory.ForYou,
      section = TemplateSection.Featured,
      aspectRatio = AspectRatio.Ratio4x5,
      gradient = listOf(Color(0xFF2B1B12), Color(0xFF8D4E1D), Color(0xFFD98E3B)),
    ),
    Template(
      "t-morning-muse",
      "Morning Muse",
      TemplateCategory.Lifestyle,
      TemplateSection.Trending,
      isPro = false,
      mediaCount = 7,
      gradient = listOf(Color(0xFF6B4A38), Color(0xFFD9C2A3))
    ),
    Template(
      "t-mirror-soul",
      "Mirror Soul",
      TemplateCategory.Lifestyle,
      TemplateSection.Trending,
      isPro = true,
      gradient = listOf(Color(0xFF1A1414), Color(0xFF4A3038))
    ),
    Template(
      "t-cafe-collage",
      "Café Collage",
      TemplateCategory.Business,
      TemplateSection.Trending,
      isPro = true,
      gradient = listOf(Color(0xFF22303C), Color(0xFF6E8CA0))
    ),
    Template(
      "t-candlelit",
      "Candlelit",
      TemplateCategory.Collage,
      TemplateSection.Trending,
      isPro = true,
      gradient = listOf(Color(0xFF26170F), Color(0xFF6B3A1F))
    ),
    Template(
      "t-street-style",
      "Street Style",
      TemplateCategory.Business,
      TemplateSection.Trending,
      isPro = false,
      gradient = listOf(Color(0xFF20242A), Color(0xFF5A6472))
    ),
    Template(
      "t-golden-hour",
      "Golden Hour",
      TemplateCategory.Collage,
      TemplateSection.Trending,
      isPro = false,
      gradient = listOf(Color(0xFF3A2C14), Color(0xFFC98A3D))
    ),
    Template(
      "f-afterhours-01",
      "Afterhours 01",
      TemplateCategory.Lifestyle,
      TemplateSection.Filters,
      isPro = true,
      gradient = listOf(Color(0xFF10131A), Color(0xFF3D2E5C))
    ),
    Template(
      "f-sunwave-01",
      "Sunwave 01",
      TemplateCategory.Lifestyle,
      TemplateSection.Filters,
      isPro = true,
      gradient = listOf(Color(0xFF5B3A1E), Color(0xFFE8A13D))
    ),
    Template(
      "f-saffron-08",
      "Saffron 08",
      TemplateCategory.Business,
      TemplateSection.Filters,
      isPro = true,
      gradient = listOf(Color(0xFF0E3B33), Color(0xFF2E8B6B))
    ),
    Template(
      "f-oatmilk-04",
      "Oatmilk 04",
      TemplateCategory.Collage,
      TemplateSection.Filters,
      isPro = false,
      gradient = listOf(Color(0xFFE8DCCB), Color(0xFFC9B8A0))
    ),
    Template(
      "f-midnight-02",
      "Midnight 02",
      TemplateCategory.Business,
      TemplateSection.Filters,
      isPro = true,
      gradient = listOf(Color(0xFF0B0E14), Color(0xFF2B3A55))
    ),
    Template(
      "f-bloom-05",
      "Bloom 05",
      TemplateCategory.Collage,
      TemplateSection.Filters,
      isPro = false,
      gradient = listOf(Color(0xFF4A2C3A), Color(0xFFC98BA6))
    ),
  )
