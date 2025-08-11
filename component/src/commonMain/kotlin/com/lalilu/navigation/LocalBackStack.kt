package com.lalilu.navigation

import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.staticCompositionLocalOf

val LocalBackStack = staticCompositionLocalOf<SnapshotStateList<Screen>> {
    error("No back stack provided")
}