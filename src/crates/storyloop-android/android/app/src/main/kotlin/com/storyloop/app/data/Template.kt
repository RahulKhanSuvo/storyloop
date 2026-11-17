package com.storyloop.app.data

import androidx.compose.ui.graphics.Color

/** Social format a template targets. */
enum class AspectRatio(val value: Float) {
  Ratio9x16(9f / 16f),
  Ratio4x5(4f / 5f),
  Ratio1x1(1f),
}

/** Feed placement of a template on the home screen. */
enum class TemplateSection { Featured, Trending, Filters }

/** Discovery categories shown as tabs on the home screen. */
enum class TemplateCategory { ForYou, Lifestyle, Business, Collage }

/**
 * A reusable video template: a preset composition (media slots, text, colors,
 * animations) users customize instead of editing from scratch.
 *
 * [gradient] is placeholder art until real thumbnail media is wired in.
 */
data class Template(
  val id: String,
  val title: String,
  val category: TemplateCategory,
  val section: TemplateSection,
  val aspectRatio: AspectRatio = AspectRatio.Ratio9x16,
  val isPro: Boolean = false,
  val mediaCount: Int = 0,
  val gradient: List<Color> = emptyList(),
)
