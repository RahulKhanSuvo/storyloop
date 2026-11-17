package com.storyloop.app.ui.home

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.storyloop.app.data.Template
import com.storyloop.app.data.TemplateCategory
import com.storyloop.app.data.TemplateRepository
import com.storyloop.app.data.TemplateSection
import com.storyloop.app.ui.theme.StoryLoopTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.junit.Rule
import org.junit.Test

/** UI tests for [HomeScreen]. */
class HomeScreenTest {

  @get:Rule
  val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun homeScreen_showsFeaturedCtaAndSections() {
    composeTestRule.setContent {
      StoryLoopTheme {
        HomeScreen(viewModel = HomeViewModel(FakeTemplateRepository()))
      }
    }
    composeTestRule.onNodeWithText("Create Reels").assertExists()
    composeTestRule.onNodeWithText("Trending").assertExists()
    composeTestRule.onNodeWithText("Filters").assertExists()
  }
}

private class FakeTemplateRepository : TemplateRepository {
  override val templates: Flow<List<Template>> = flow { emit(FIXED_CATALOG) }
}

private val FIXED_CATALOG =
  listOf(
    Template("featured", "Featured", TemplateCategory.ForYou, TemplateSection.Featured),
    Template("t1", "Trend", TemplateCategory.Lifestyle, TemplateSection.Trending),
    Template("f1", "Filter", TemplateCategory.Lifestyle, TemplateSection.Filters),
  )
