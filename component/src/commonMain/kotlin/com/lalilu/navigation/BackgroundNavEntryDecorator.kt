package com.lalilu.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey

@Composable
fun rememberDefaultBackgroundColorNavEntryDecorator(): NavEntryDecorator<NavKey> {
    return NavEntryDecorator { entry ->
        Box(
            modifier = Modifier.background(MaterialTheme.colorScheme.background),
            content = { entry.Content() }
        )
    }
}