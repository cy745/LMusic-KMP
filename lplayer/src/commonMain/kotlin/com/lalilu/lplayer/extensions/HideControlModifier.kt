/*
 * Copyright (c) 2026 lalilu. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.lalilu.lplayer.extensions

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * 隐藏控制：触摸时显示元素，手指离开后延时隐藏。
 *
 * 移植自单端 LMusic 的 `com.lalilu.component.extension.hideControl`，语义完全一致，
 * 只有拦截手段不同：原实现用 `pointerInteropFilter`（Android 专属 API），这里改为在
 * [PointerEventPass.Initial] 阶段 `consume()` 掉指针事件，从而在 KMP 各平台行为一致。
 *
 * 行为要点：
 * - 隐藏态由 [minAlpha] 的透明度表达，元素**仍在布局中**
 * - 按下即显示（并把透明度动画到 [maxAlpha]）；抬起后 [hideDelay] 毫秒开始隐藏
 * - 未隐藏时再次按下会**取消**挂起的隐藏计时，抬起后重新计时
 * - [intercept] 为 true 时，隐藏态会吞掉指针事件：第一次点击只负责"显示出来"，
 *   显示之后的点击才会传给内部按钮（详见 [intercept]）
 *
 * @param enable        是否启用隐藏逻辑；关闭时元素恒为 [maxAlpha]
 * @param intercept     是否拦截"第一次点击的显示事件"。
 *                      true 用于 toolbar 这类内部有按钮的元素：隐藏时先点击一下只显示，
 *                      显示之后再点击才触发按钮回调。
 *                      进度条这类需要保留原有手势的元素应保持 false（默认）。
 * @param minAlpha      最小（隐藏态）透明度
 * @param maxAlpha      最大（显示态）透明度
 * @param hideDelay     手指离开后多久开始隐藏
 */
fun Modifier.hideControl(
    enable: () -> Boolean,
    intercept: () -> Boolean = { false },
    minAlpha: Float = 0f,
    maxAlpha: Float = 1f,
    hideDelay: Long = 3000L,
) = composed {
    // key 用 enable()：开关打开 / 进入展开态的那一刻会重建 state 并以 true 为初值，
    // 也就是"一进入展开态就处于隐藏态"；enable() 为 false 时初值为 false，元素保持可见。
    val isHide = remember(enable()) { mutableStateOf(enable()) }

    // 隐藏计时的触发器：-1 表示"已按下、计时取消"；抬起时自增使其发生变化，
    // 从而取消上一个 LaunchedEffect 并重新计时。单端用 System.currentTimeMillis()，
    // 这里用自增计数做到等效语义且不依赖平台时间 API。
    val showEvent = remember { mutableLongStateOf(-1L) }

    val animateAlpha = animateFloatAsState(
        targetValue = if (isHide.value) minAlpha else maxAlpha,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "HideControlAlpha",
    )

    LaunchedEffect(showEvent.longValue) {
        if (showEvent.longValue < 0) return@LaunchedEffect

        delay(hideDelay)

        // 延时期间若又被按下（showEvent 变化会取消本协程），或已处于隐藏态，则不再处理
        if (isActive && !isHide.value) {
            isHide.value = true
        }
    }

    this.graphicsLayer {
        alpha = animateAlpha.value
        // 用 ModulateAlpha 而不是默认的 Offscreen：
        // Offscreen 会额外创建一层离屏缓冲，其范围等于本节点的布局尺寸，因此"移出自身边界"的
        // 内容会在淡入淡出过程中被裁剪 —— 进度条上拖时内部会 translationY 向上位移（见
        // SeekbarLayout 的 offsetYProgress），正是这个原因导致过渡期间上方区域被裁。
        // ModulateAlpha 直接把透明度乘进每条绘制指令，不创建离屏层，所以不会裁剪。
        // 代价：内容自身相互重叠时不会按整体合成做混合（本处为进度条/文本，无重叠问题）。
        compositingStrategy = CompositingStrategy.ModulateAlpha
    }
        .enableFor(enable = enable) {
            pointerInput(Unit) {
                awaitPointerEventScope {
                    while (enable()) {
                        // 与单端一致使用 Main 阶段：只观察、不消费，因此不影响元素自身的手势
                        val event = awaitPointerEvent(PointerEventPass.Main)

                        when (event.type) {
                            PointerEventType.Press -> {
                                isHide.value = false
                                // 取消挂起的隐藏计时
                                showEvent.longValue = -1L
                            }

                            PointerEventType.Release -> {
                                // 重新计时
                                showEvent.longValue += 1L
                            }

                            else -> Unit
                        }
                    }
                }
            }
        }
        .enableFor(enable = { isHide.value && intercept() }) {
            // 隐藏态下在 Initial 阶段吞掉全部指针事件：子元素收不到，
            // 因此第一次点击只负责"显示出来"，不会触发按钮回调。
            pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        event.changes.forEach { it.consume() }
                    }
                }
            }
        }
}
