@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.lalilu.extensions

import androidx.compose.animation.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
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
    val defaultAnimationScope: AnimatedContentScope? = null,
    val defaultConfig: SharedTransitionScope.SharedContentConfig? = null
) {
    fun Modifier.sharedElementV2(
        key: String,
        config: SharedTransitionScope.SharedContentConfig = defaultConfig
            ?: SharedTransitionDefaults.SharedContentConfig,
        animationVisibilityScope: AnimatedVisibilityScope? = null
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
                animatedVisibilityScope = animationScope
            )
        }
    }

    fun Modifier.sharedBoundsV2(
        key: String,
        config: SharedTransitionScope.SharedContentConfig = defaultConfig
            ?: SharedTransitionDefaults.SharedContentConfig,
        animationVisibilityScope: AnimatedVisibilityScope? = null,
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
                animatedVisibilityScope = animationScope
            )
        }
    }

    fun Modifier.sharedElementWithCallerManagedVisibilityV2(
        key: String,
        visible: Boolean,
        config: SharedTransitionScope.SharedContentConfig = defaultConfig
            ?: SharedTransitionDefaults.SharedContentConfig
    ) = composed {
        val transitionScope = sharedTransitionScope
            ?: return@composed this@composed

        val sharedConstantKey = sharedMap[key]
            ?: return@composed this@composed


        with(transitionScope) {
            this@composed.sharedElementWithCallerManagedVisibility(
                sharedContentState = rememberSharedContentState(sharedConstantKey, config),
                visible = visible
            )
        }
    }
}

@Composable
fun SharedContext(
    sharedMap: SharedMap = LocalSharedMap.current,
    sharedTransitionScope: SharedTransitionScope? = null,
    defaultAnimationScope: AnimatedContentScope? = null,
    defaultConfig: SharedTransitionScope.SharedContentConfig? = null,
    block: @Composable SharedContextScope.() -> Unit
) {
    val sharedScope = runCatching { sharedTransitionScope ?: LocalSharedTransitionScope.current }
        .getOrNull()
    val animationScope = runCatching { defaultAnimationScope ?: LocalNavAnimatedContentScope.current }
        .getOrNull()

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