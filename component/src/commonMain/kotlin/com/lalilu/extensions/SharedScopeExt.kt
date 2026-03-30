/*
 * Copyright (c) 2026 lalilu. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.lalilu.extensions

import androidx.compose.animation.*
import androidx.compose.animation.SharedTransitionScope.*
import androidx.compose.animation.SharedTransitionScope.PlaceholderSize.Companion.ContentSize
import androidx.compose.animation.SharedTransitionScope.ResizeMode.Companion.scaleToBounds
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import com.lalilu.navigation.LocalSharedTransitionScope

typealias SharedMap = Map<String, String>

val LocalSharedMap = staticCompositionLocalOf<SharedMap> { emptyMap() }


@OptIn(InternalComposeApi::class)
@Composable
fun rememberSharedMap(
    id: String,
    keys: List<String>,
    prefix: String = "",
): SharedMap {
    val hash = currentComposer.compositeKeyHashCode

    return remember(id, keys, prefix) {
        buildSharedMap(
            id = id,
            keys = keys,
            prefix = "[$hash]$prefix"
        )
    }
}

fun buildSharedMap(
    id: String,
    keys: List<String>,
    prefix: String = "",
): SharedMap {
    return keys.associateWith { "$prefix$it:$id" }
}

class SharedContextScope(
    val sharedMap: SharedMap,
    val sharedTransitionScope: SharedTransitionScope? = null,
    val defaultAnimationScope: AnimatedVisibilityScope? = null,
    val defaultConfig: SharedContentConfig? = null
) {
    companion object {
        internal val PREVIEW_SCOPE by lazy {
            SharedContextScope(
                sharedMap = emptyMap(),
                sharedTransitionScope = null,
                defaultAnimationScope = null,
                defaultConfig = null
            )
        }
    }

    fun Modifier.sharedElementV2(
        key: String,
        config: SharedContentConfig = defaultConfig
            ?: SharedTransitionDefaults.SharedContentConfig,
        animationVisibilityScope: AnimatedVisibilityScope? = null,
        boundsTransform: BoundsTransform = SharedTransitionDefaults.BoundsTransform,
        placeholderSize: PlaceholderSize = ContentSize,
        renderInOverlayDuringTransition: Boolean = true,
        zIndexInOverlay: Float = 0f,
        clipInOverlayDuringTransition: OverlayClip = ParentClip,
    ): Modifier = composed {
        val transitionScope = sharedTransitionScope
            ?: return@composed this@composed

        val sharedConstantKey = sharedMap[key]
            ?: return@composed this@composed

        val animationScope = animationVisibilityScope
            ?: defaultAnimationScope
            ?: return@composed this@composed

        with(transitionScope) {
            this@composed.sharedElement(
                sharedContentState = rememberSharedContentState(sharedConstantKey, config),
                animatedVisibilityScope = animationScope,
                boundsTransform = boundsTransform,
                placeholderSize = placeholderSize,
                renderInOverlayDuringTransition = renderInOverlayDuringTransition,
                zIndexInOverlay = zIndexInOverlay,
                clipInOverlayDuringTransition = clipInOverlayDuringTransition
            )
        }
    }

    fun Modifier.sharedBoundsV2(
        key: String,
        config: SharedContentConfig = defaultConfig ?: SharedTransitionDefaults.SharedContentConfig,
        animationVisibilityScope: AnimatedVisibilityScope? = null,
        enter: EnterTransition = fadeIn(),
        exit: ExitTransition = fadeOut(),
        boundsTransform: BoundsTransform = SharedTransitionDefaults.BoundsTransform,
        resizeMode: ResizeMode = scaleToBounds(ContentScale.FillWidth, Center),
        placeholderSize: PlaceholderSize = ContentSize,
        renderInOverlayDuringTransition: Boolean = true,
        zIndexInOverlay: Float = 0f,
        clipInOverlayDuringTransition: OverlayClip = ParentClip,
    ) = composed {
        val transitionScope = sharedTransitionScope
            ?: return@composed this@composed

        val sharedConstantKey = sharedMap[key]
            ?: return@composed this@composed

        val animationScope = animationVisibilityScope
            ?: defaultAnimationScope
            ?: return@composed this@composed

        with(transitionScope) {
            this@composed.sharedBounds(
                sharedContentState = rememberSharedContentState(sharedConstantKey, config),
                animatedVisibilityScope = animationScope,
                enter = enter,
                exit = exit,
                boundsTransform = boundsTransform,
                resizeMode = resizeMode,
                placeholderSize = placeholderSize,
                renderInOverlayDuringTransition = renderInOverlayDuringTransition,
                zIndexInOverlay = zIndexInOverlay,
                clipInOverlayDuringTransition = clipInOverlayDuringTransition
            )
        }
    }

    fun Modifier.sharedElementWithCallerManagedVisibilityV2(
        key: String,
        visible: Boolean,
        config: SharedContentConfig = defaultConfig
            ?: SharedTransitionDefaults.SharedContentConfig,
        boundsTransform: BoundsTransform = SharedTransitionDefaults.BoundsTransform,
        placeholderSize: PlaceholderSize = ContentSize,
        renderInOverlayDuringTransition: Boolean = true,
        zIndexInOverlay: Float = 0f,
        clipInOverlayDuringTransition: OverlayClip = ParentClip,
    ) = composed {
        val transitionScope = sharedTransitionScope
            ?: return@composed this@composed

        val sharedConstantKey = sharedMap[key]
            ?: return@composed this@composed


        with(transitionScope) {
            this@composed.sharedElementWithCallerManagedVisibility(
                sharedContentState = rememberSharedContentState(sharedConstantKey, config),
                visible = visible,
                boundsTransform = boundsTransform,
                placeholderSize = placeholderSize,
                renderInOverlayDuringTransition = renderInOverlayDuringTransition,
                zIndexInOverlay = zIndexInOverlay,
                clipInOverlayDuringTransition = clipInOverlayDuringTransition
            )
        }
    }

    fun Modifier.skipToLookaheadSizeV2(
        enabled: (() -> Boolean)? = null
    ) = composed {
        val transitionScope = sharedTransitionScope
            ?: return@composed this@composed

        with(transitionScope) {
            if (enabled != null) this@composed.skipToLookaheadSize(enabled)
            else this@composed.skipToLookaheadSize()
        }
    }

    fun Modifier.skipToLookaheadPosition(
        enabled: (() -> Boolean)? = null
    ) = composed {
        val transitionScope = sharedTransitionScope
            ?: return@composed this@composed

        with(transitionScope) {
            if (enabled != null) this@composed.skipToLookaheadPosition(enabled)
            else this@composed.skipToLookaheadPosition()
        }
    }

    fun Modifier.renderInSharedTransitionScopeOverlayV2(
        zIndexInOverlay: Float = 0f,
        renderInOverlay: (() -> Boolean)? = null,
    ): Modifier = composed {
        val transitionScope = sharedTransitionScope
            ?: return@composed this@composed

        with(transitionScope) {
            this@composed.renderInSharedTransitionScopeOverlay(
                zIndexInOverlay = zIndexInOverlay,
                renderInOverlay = renderInOverlay ?: { transitionScope.isTransitionActive }
            )
        }
    }

    private val ParentClip: OverlayClip =
        object : OverlayClip {
            override fun getClipPath(
                sharedContentState: SharedContentState,
                bounds: Rect,
                layoutDirection: LayoutDirection,
                density: Density,
            ): Path? {
                return sharedContentState.parentSharedContentState?.clipPathInOverlay
            }
        }
}


@Suppress("ILLEGAL_RUN_CATCHING_AROUND_COMPOSABLE")
@Composable
fun SharedContext(
    sharedMap: SharedMap = LocalSharedMap.current,
    sharedTransitionScope: SharedTransitionScope? = null,
    defaultAnimationScope: AnimatedContentScope? = null,
    defaultConfig: SharedContentConfig? = null,
    block: @Composable SharedContextScope.() -> Unit
) {
    if (LocalInspectionMode.current) {
        SharedContextScope.PREVIEW_SCOPE.block()
        return
    }

    val sharedScope = sharedTransitionScope ?: LocalSharedTransitionScope.current
    val animationScope = defaultAnimationScope ?: runCatching { LocalNavAnimatedContentScope.current }.getOrNull()

    val scope = remember(
        sharedMap,
        defaultConfig,
        sharedScope,
        animationScope
    ) {
        SharedContextScope(
            sharedMap = sharedMap,
            defaultConfig = defaultConfig,
            sharedTransitionScope = sharedScope,
            defaultAnimationScope = animationScope
        )
    }

    CompositionLocalProvider(LocalSharedMap provides sharedMap) {
        scope.block()
    }
}