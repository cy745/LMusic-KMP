package com.lalilu.navigation

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation3.runtime.NavBackStack

val LocalBackStack = staticCompositionLocalOf<NavBackStack<Screen>> {
    error("No back stack provided")
}