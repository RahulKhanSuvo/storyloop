package com.storyloop.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.storyloop.app.data.Template
import com.storyloop.app.data.TemplateCategory
import com.storyloop.app.data.TemplateRepository
import com.storyloop.app.data.TemplateSection
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

sealed interface HomeUiState {
  data object Loading : HomeUiState

  data class Error(val throwable: Throwable) : HomeUiState

  data class Success(
    val selectedCategory: TemplateCategory = TemplateCategory.ForYou,
    val featured: Template? = null,
    val trending: List<Template> = emptyList(),
    val filters: List<Template> = emptyList(),
  ) : HomeUiState
}

class HomeViewModel(templateRepository: TemplateRepository) : ViewModel() {

  private val selectedCategory =
    kotlinx.coroutines.flow.MutableStateFlow(TemplateCategory.ForYou)

  val uiState: StateFlow<HomeUiState> =
    combine<List<Template>, TemplateCategory, HomeUiState>(
      templateRepository.templates,
      selectedCategory,
    ) { templates, category ->
      HomeUiState.Success(
        selectedCategory = category,
        featured = templates.firstOrNull { it.section == TemplateSection.Featured },
        trending =
          templates.filter {
            it.section == TemplateSection.Trending &&
              (category == TemplateCategory.ForYou || it.category == category)
          },
        filters =
          templates.filter {
            it.section == TemplateSection.Filters &&
              (category == TemplateCategory.ForYou || it.category == category)
          },
      )
    }
      .catch { emit(HomeUiState.Error(it)) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState.Loading)

  fun selectCategory(category: TemplateCategory) {
    selectedCategory.value = category
  }
}
