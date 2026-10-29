package com.lalilu.lhome.screen

import androidx.compose.animation.animateBounds
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.layout.layout
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import com.lalilu.component.AdaptiveLookaheadScope
import com.lalilu.component.LocalAdaptiveLayoutParent
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class AdaptiveLookaheadTest {
    @Test fun boundsAnimationsSurviveRepeatedMovesBetweenDetachedSheetParents() = runDesktopComposeUiTest {
        val wide = mutableStateOf(false)
        val width = mutableStateOf(180.dp)
        setContent {
            // A shared-transition root merges nested lookahead scopes, as in App.
            LookaheadScope {
                val page = remember {
                    movableContentOf<Boolean> { parent ->
                        CompositionLocalProvider(LocalAdaptiveLayoutParent provides parent) {
                            AdaptiveLookaheadScope {
                                Text("retained page", Modifier.width(width.value).animateBounds(this))
                            }
                        }
                    }
                }
                key(wide.value) {
                    Box(Modifier.layout { measurable, constraints ->
                        val child = measurable.measure(constraints)
                        layout(child.width, child.height) {
                            withMotionFrameOfReferencePlacement { child.place(0, 0) }
                        }
                    }) { page(wide.value) }
                }
            }
        }
        repeat(6) {
            runOnIdle { width.value = if (it % 2 == 0) 260.dp else 180.dp }
            waitForIdle()
            runOnIdle { wide.value = !wide.value }
            onNodeWithText("retained page").assertIsDisplayed()
        }
    }
}
