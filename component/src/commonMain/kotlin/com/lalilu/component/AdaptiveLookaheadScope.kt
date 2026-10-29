package com.lalilu.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.layout.LookaheadScope

/** The shell supplies its actual parent identity, rather than independently sampling window size. */
val LocalAdaptiveLayoutParent = compositionLocalOf<Any> { false }

/**
 * Recreate bounds animations when the adaptive shell reparents its movable page content.
 * Compose 1.11 animateBounds caches motion-frame ancestor coordinates (including bottom
 * sheets). Reusing those nodes after the old shell detaches reads detached coordinates.
 * Ordinary resizing within one shell keeps the nodes and their bounds animations intact.
 */
@Composable
fun AdaptiveLookaheadScope(
    parentKey: Any = LocalAdaptiveLayoutParent.current,
    content: @Composable LookaheadScope.() -> Unit,
) {
    key(parentKey) {
        LookaheadScope(content = content)
    }
}
