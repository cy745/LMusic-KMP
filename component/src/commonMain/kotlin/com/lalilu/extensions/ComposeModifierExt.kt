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

package com.lalilu.extensions

import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.lalilu.common.ext.io
import kotlinx.coroutines.*

/**
 * 可自定义长按回调触发时长的Modifier
 */
fun Modifier.longClickable(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onTap: () -> Unit = {},
    onRelease: () -> Unit = {},
    enableHaptic: Boolean = true,
    longClickMinTimeMillis: Long = 1000L,
    indication: Indication? = null,
    interactionSource: MutableInteractionSource,
): Modifier = composed {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    this
        .semantics { role = Role.Button }
        .indication(interactionSource, indication ?: LocalIndication.current)
        .hoverable(interactionSource, true)
        .pointerInput(Unit) {
            var timer: Job?

            detectTapGestures(
                onPress = {
                    val press = PressInteraction.Press(it)
                    interactionSource.emit(press)
                    onTap()

                    // tap的瞬间开始计时器
                    timer = scope.launch(Dispatchers.io) {
                        delay(longClickMinTimeMillis)
                        if (!isActive) return@launch

                        onLongClick()
                        if (enableHaptic) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    }

                    // 阻塞直到松手
                    if (tryAwaitRelease()) {
                        interactionSource.emit(PressInteraction.Release(press))
                    } else {
                        interactionSource.emit(PressInteraction.Cancel(press))
                    }

                    // 取消计时器
                    timer?.cancel()
                    onRelease()
                },
                onTap = { onClick() },
                onLongPress = {}
            )
        }
}