package com.lalilu.lplayer.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.sign
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.lalilu.RemixIcon
import org.jetbrains.compose.resources.vectorResource
import kotlin.math.roundToInt

/** Physical left/right gestures; queue policy remains owned by Playback. */
@Composable
internal fun SwipeableQueueItem(
    modifier: Modifier = Modifier,
    random: Boolean,
    onPlayNext: () -> Unit,
    onRemove: () -> Unit,
    content: @Composable () -> Unit,
) {
    var drag by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val playNext by rememberUpdatedState(onPlayNext)
    val remove by rememberUpdatedState(onRemove)
    val offset by animateFloatAsState(drag, if (dragging) snap() else tween(180), label = "QueueSwipe")
    val density = LocalDensity.current
    BoxWithConstraints(modifier.clipToBounds().semantics {
        customActions = listOf(
            CustomAccessibilityAction("下一首播放") { playNext(); true },
            CustomAccessibilityAction("移出播放列表") { remove(); true },
        )
    }) {
        val threshold = with(density) { minOf(96.dp, maxWidth * .3f).toPx() }
        val limit = with(density) { maxWidth.toPx() }
        if (offset != 0f) {
            val removing = offset > 0
            val color = if (removing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            Row(Modifier.matchParentSize().background(color.copy(alpha = .10f)).padding(horizontal = 20.dp),
                horizontalArrangement = if (removing) Arrangement.Start else Arrangement.End,
                verticalAlignment = Alignment.CenterVertically) {
                Icon(vectorResource(if (removing) RemixIcon.System.deleteBinLine
                    else if (random) RemixIcon.Editor.insertRowTop else RemixIcon.Editor.insertRowBottom),
                    contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (removing) "移出列表" else "下一首播放", color = color,
                    style = MaterialTheme.typography.labelMedium)
            }
        }
        // Keep the detector stationary while the visual card moves underneath it.
        Box(Modifier.pointerInput(threshold, limit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                var total = Offset.Zero
                var horizontal = false
                var cancelled = false
                try {
                    while (true) {
                        // Once owned, consume every movement before the list sees it, including
                        // vertical-only drift. Main-pass callbacks for horizontal deltas alone
                        // cannot define the ownership of a diagonal gesture.
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (horizontal && event.changes.count { it.pressed } > 1) cancelled = true
                        if (cancelled) {
                            event.changes.forEach { it.consume() }
                            if (event.changes.none { it.pressed }) break
                            continue
                        }
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.isConsumed || (!horizontal && event.changes.count { it.pressed } > 1)) break
                        val delta = change.positionChange()
                        if (!horizontal) {
                            total += delta
                            val slop = viewConfiguration.touchSlop
                            if (maxOf(abs(total.x), abs(total.y)) <= slop) {
                                if (!change.pressed) break
                                continue
                            }
                            // Do not try to claim this gesture again after yielding vertically.
                            if (abs(total.y) >= abs(total.x)) break
                            horizontal = true
                            dragging = true
                            drag = (total.x - sign(total.x) * slop).coerceIn(-limit, limit)
                        } else {
                            drag = (drag + delta.x).coerceIn(-limit, limit)
                        }
                        event.changes.forEach { it.consume() }
                        if (!change.pressed) {
                            if (drag <= -threshold) playNext()
                            else if (drag >= threshold) remove()
                            break
                        }
                    }
                } finally {
                    dragging = false
                    drag = 0f
                }
            }
        }) {
            Box(Modifier.offset { IntOffset(offset.roundToInt(), 0) }
                .background(MaterialTheme.colorScheme.background)) { content() }
        }
    }
}
