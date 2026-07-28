package com.lalilu.component

import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.runtime.Composable

fun interface LazyStaggeredGridContent {

    @Composable
    fun register(): LazyStaggeredGridScope.() -> Unit
}