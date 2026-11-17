package com.storyloop.app.data

import android.Manifest
import android.os.Build

/**
 * The media permissions to ask for, by OS level: 33 splits images and video,
 * 34 adds user-selected partial access.
 */
fun mediaPermissions(): List<String> =
  if (Build.VERSION.SDK_INT >= 33) {
    buildList {
      add(Manifest.permission.READ_MEDIA_IMAGES)
      add(Manifest.permission.READ_MEDIA_VIDEO)
      if (Build.VERSION.SDK_INT >= 34) {
        add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
      }
    }
  } else {
    listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
  }
