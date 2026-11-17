package com.storyloop.app

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Main : NavKey

/** The editor over one canvas format: "reel", "portrait", "square" or "landscape". */
@Serializable data class Editor(val format: String) : NavKey
