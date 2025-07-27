package com.lalilu.component

import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.runtime.Composable


interface LazyGridContent {

    @Composable
    fun register(): LazyGridScope.() -> Unit
}