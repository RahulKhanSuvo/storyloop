package com.storyloop.app.ui.home

import com.storyloop.app.data.Template
import com.storyloop.app.data.TemplateCategory
import com.storyloop.app.data.TemplateRepository
import com.storyloop.app.data.TemplateSection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

  private val mainDispatcher = UnconfinedTestDispatcher()

  @Before
  fun setUp() {
    Dispatchers.setMain(mainDispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun uiState_initiallyLoading() {
    val viewModel = HomeViewModel(FakeTemplateRepository())
    assertEquals(HomeUiState.Loading, viewModel.uiState.value)
  }

  @Test
  fun uiState_success_splitsFeaturedTrendingAndFilters() = runTest {
    val viewModel = HomeViewModel(FakeTemplateRepository())
    backgroundScope.launch { viewModel.uiState.collect {} }

    val success = viewModel.uiState.filterIsInstance<HomeUiState.Success>().first()

    assertEquals("featured", success.featured?.id)
    assertEquals(2, success.trending.size)
    assertEquals(2, success.filters.size)
  }

  @Test
  fun selectCategory_filtersListsToThatCategory() = runTest {
    val viewModel = HomeViewModel(FakeTemplateRepository())
    backgroundScope.launch { viewModel.uiState.collect {} }
    viewModel.uiState.filterIsInstance<HomeUiState.Success>().first() // wait for initial load

    viewModel.selectCategory(TemplateCategory.Lifestyle)

    val success = viewModel.uiState.filterIsInstance<HomeUiState.Success>().first()
    assertEquals(TemplateCategory.Lifestyle, success.selectedCategory)
    assertTrue(success.trending.all { it.category == TemplateCategory.Lifestyle })
    assertTrue(success.filters.all { it.category == TemplateCategory.Lifestyle })
    assertEquals("featured", success.featured?.id) // hero stays on every tab
  }
}

private class FakeTemplateRepository : TemplateRepository {
  override val templates: Flow<List<Template>> = flow { emit(FIXED_CATALOG) }
}

private val FIXED_CATALOG =
  listOf(
    Template("featured", "Featured", TemplateCategory.ForYou, TemplateSection.Featured),
    Template("t1", "Lifestyle Trend", TemplateCategory.Lifestyle, TemplateSection.Trending),
    Template("t2", "Business Trend", TemplateCategory.Business, TemplateSection.Trending),
    Template("f1", "Lifestyle Filter", TemplateCategory.Lifestyle, TemplateSection.Filters),
    Template("f2", "Business Filter", TemplateCategory.Business, TemplateSection.Filters),
  )
