package com.storyloop.app

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.storyloop.app.ui.editor.EditorScreen
import com.storyloop.app.ui.home.HomeScreen

@Composable
fun MainNavigation() {
  val backStack = rememberNavBackStack(Main)

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider =
      entryProvider {
        entry<Main> {
          HomeScreen(onOpenEditor = { format -> backStack.add(Editor(format)) })
        }
        entry<Editor> { key ->
          EditorScreen(format = key.format, onBack = { backStack.removeLastOrNull() })
        }
      },
  )
}
