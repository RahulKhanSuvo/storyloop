package com.storyloop.app

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Main : NavKey

/** The full-screen create picker: template row up top, device media below. */
@Serializable data object Create : NavKey

/** One media item picked on the create screen, headed for the editor. */
@Serializable data class StartMedia(val uri: String, val isVideo: Boolean, val durationMs: Long)

/** The editor over one canvas format; `media` loads onto its main track on open. */
@Serializable data class Editor(val format: String, val media: List<StartMedia> = emptyList()) : NavKey
