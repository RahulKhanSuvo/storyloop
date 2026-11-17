package com.storyloop.app

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.storyloop.app.ui.editor.EditorScreen
import com.storyloop.app.ui.home.HomeScreen
import com.storyloop.app.ui.start.StartScreen

@Composable
fun MainNavigation() {
  val backStack = rememberNavBackStack(Main)

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider =
      entryProvider {
        entry<Main> {
          HomeScreen(onOpenCreate = { backStack.add(Create) })
        }
        entry<Create> {
          StartScreen(
            onClose = { backStack.removeLastOrNull() },
            onOpenEditor = { media -> backStack.add(Editor(format = "reel", media = media)) },
          )
        }
        entry<Editor> { key ->
          EditorScreen(
            format = key.format,
            initialMedia = key.media,
            onBack = { backStack.removeLastOrNull() },
          )
        }
      },
  )
}
