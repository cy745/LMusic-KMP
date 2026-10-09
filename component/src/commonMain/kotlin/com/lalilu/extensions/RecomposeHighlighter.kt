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

package com.lalilu.extensions

import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile
import kotlin.math.log2
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * 计数到多少之后颜色饱和：再多的重组也不会更红。
 * 取 32 是因为颜色按 `log2` 映射，1/2/4/8/16/32 这几档能明显区分开。
 */
private const val SaturateCount = 32

/** key 标签最多往上堆几行（超出后强行落在最后一行）。 */
private const val MaxKeyLabelRows = 8

private val KeyLabelStyle = TextStyle(
    color = Color.Black,
    fontSize = 10.sp,
    fontWeight = FontWeight.Bold,
)

private val KeyLabelPaddingX = 6.dp
private val KeyLabelPaddingY = 2.dp
private val KeyLabelMargin = 6.dp
private val KeyLabelGap = 4.dp
private val KeyLabelCorner = 3.dp

/**
 * 计数 → 颜色：1 次重组是绿色，累积到 [SaturateCount] 次变红（绿→黄→红），
 * 透明度同时从 0.45 升到 1 —— 单次重组只是一闪，连续重组会越来越显眼。
 */
private fun recomposeHighlightColor(count: Int): Color {
    val saturated = log2((SaturateCount + 1).toFloat())
    val progress = (log2((count + 1).toFloat()) / saturated).coerceIn(0f, 1f)

    return Color.hsv(
        hue = lerp(140f, 0f, progress),
        saturation = 0.9f,
        value = 1f,
        alpha = lerp(0.45f, 1f, progress),
    )
}

/**
 * 多个 highlighter 的 key 标签共享的避让表（进程内单例）。
 *
 * 每个 highlighter 绘制前登记自己的标签矩形（[place]），从下往上找第一个不与**其它**已登记
 * 矩形重叠的行；计数归零或节点 detach 时释放（[release]），避免"没在重组还占着行"。
 *
 * 不加锁：绘制/组合都在各自的 UI 线程上，这里只用「整体替换不可变快照」的方式更新，
 * 最坏情况是跨渲染线程读到一帧前的布局，不会读到结构损坏的 map。
 */
internal object RecomposeHighlighterKeys {

    @Volatile
    private var placements: Map<Any, Rect> = emptyMap()

    /**
     * 为 [id] 选一个不与其它 highlighter 标签重叠的矩形。
     *
     * @param candidate 给定行号（0 是最靠近左下角的一行）返回该行的候选矩形
     */
    fun place(id: Any, candidate: (row: Int) -> Rect): Rect {
        val current = placements
        val others = current.entries.filter { it.key !== id }.map { it.value }

        for (row in 0 until MaxKeyLabelRows) {
            val rect = candidate(row)
            if (others.none { it.overlaps(rect) }) {
                placements = current + (id to rect)
                return rect
            }
        }

        // 行数不够时退化成最后一行，宁可重叠也不越界
        return candidate(MaxKeyLabelRows - 1).also { placements = current + (id to it) }
    }

    /** 释放 [id] 的占位（计数归零 / 节点 detach）。 */
    fun release(id: Any) {
        val current = placements
        if (id in current) placements = current - id
    }
}

/**
 * 重组可视化（调试用）：统计**一段时间窗口内**的重组次数，用边框颜色和左下角标签表达
 * "这段时间里重组了多少次、是哪一处"。
 *
 * 行为：
 * - 宿主作用域每重组一次，边框颜色就往红走一档（绿 → 黄 → 红，见 [recomposeHighlightColor]），
 *   同时左下角标签显示 `key ×次数`；
 * - 距离上一次重组超过 [resetAfter] 就归零，边框与标签一起消失；
 * - 纯重绘（动画、滚动）不会计数，也不会换色；
 * - 多个 highlighter 的标签通过 [RecomposeHighlighterKeys] 自动避让：
 *   左下角堆叠，互不覆盖。
 *
 * 参考实现：Google 官方 snippets 的 RecomposeHighlighter
 * （https://github.com/android/snippets/tree/master/compose/recomposehighlighter ），
 * 这里按其「计数 + 颜色区分 + 静默后归零」的语义重写为 Modifier.Node 版本，
 * 并补上了 key 标签与共享避让表。
 *
 * 用法（写在 composable 里，链在需要观察的 Modifier 上）：
 * ```kotlin
 * Box(
 *     modifier = Modifier
 *         .fillMaxSize()
 *         .recomposeHighlighter(key = "PlayerBackground")   // 观察外层作用域
 * )
 *
 * Image(
 *     modifier = Modifier
 *         .fillMaxSize()
 *         .recomposeHighlighter(key = "cover", inset = 12.dp)  // 观察内容 lambda 的作用域
 * )
 * ```
 *
 * @param key 左下角标签文字（`null` 表示只画边框不画标签）；给相邻的多个作用域不同的 key，
 *            配合 [inset] 就能一眼看出是哪一层在重组
 * @param enabled 置 `false` 时原样返回（不挂任何东西），便于统一开关
 * @param strokeWidth 边框粗细
 * @param inset 边框内缩距离；给相邻的多个作用域不同的值，就能一眼看出是哪一层在重组
 * @param resetAfter 静默多久之后计数归零（窗口长度）
 */
fun Modifier.recomposeHighlighter(
    key: String? = null,
    enabled: Boolean = true,
    strokeWidth: Dp = 4.dp,
    inset: Dp = 0.dp,
    resetAfter: Duration = 500.milliseconds,
): Modifier {
    if (!enabled) return this

    // 标签要在绘制期测量，TextMeasurer 只能从组合里取
    return composed {
        val textMeasurer = rememberTextMeasurer()

        this then RecomposeHighlighterElement(
            key = key,
            strokeWidth = strokeWidth,
            inset = inset,
            resetAfter = resetAfter,
            textMeasurer = textMeasurer,
        )
    }
}

private class RecomposeHighlighterElement(
    private val key: String?,
    private val strokeWidth: Dp,
    private val inset: Dp,
    private val resetAfter: Duration,
    private val textMeasurer: TextMeasurer,
) : ModifierNodeElement<RecomposeHighlighterNode>() {

    override fun create(): RecomposeHighlighterNode =
        RecomposeHighlighterNode(key, strokeWidth, inset, resetAfter, textMeasurer)

    override fun update(node: RecomposeHighlighterNode) {
        node.key = key
        node.strokeWidth = strokeWidth
        node.inset = inset
        node.resetAfter = resetAfter
        node.textMeasurer = textMeasurer

        // 节点参数本身不会变，能走到 update 就说明宿主作用域又重组了一次
        node.onHostRecomposed()
    }

    // 宿主每次重组都会重新构造这个元素，这里永远判为"已变化"，
    // 于是每次重组都会调用 update()，计数才有意义。
    override fun equals(other: Any?): Boolean = false

    override fun hashCode(): Int = 0
}

private class RecomposeHighlighterNode(
    var key: String?,
    var strokeWidth: Dp,
    var inset: Dp,
    var resetAfter: Duration,
    var textMeasurer: TextMeasurer,
) : Modifier.Node(), DrawModifierNode {

    private var recomposeCount = 0
    private var resetJob: Job? = null

    private var measuredLabel: String? = null
    private var measuredLayout: TextLayoutResult? = null

    /**
     * 宿主作用域重组了一次：计数 +1，并把"静默归零"的计时重新起算。
     *
     * 每次重组都取消-重启计时：连续重组期间计时一直被推迟，
     * 只有在最后一次重组之后静默满 [resetAfter] 才会归零。
     */
    fun onHostRecomposed() {
        recomposeCount++

        resetJob?.cancel()
        resetJob = coroutineScope.launch {
            delay(resetAfter)
            recomposeCount = 0
            // 计数只被本节点的 draw 读取，归零后必须显式失效一次
            invalidateDraw()
        }

        invalidateDraw()
    }

    override fun ContentDrawScope.draw() {
        drawContent()

        if (recomposeCount <= 0) {
            // 没在重组就别继续占着标签的避让行
            RecomposeHighlighterKeys.release(this)
            return
        }

        val color = recomposeHighlightColor(recomposeCount)
        val insetPx = inset.toPx()

        val borderSize = Size(
            width = (size.width - insetPx * 2f).coerceAtLeast(0f),
            height = (size.height - insetPx * 2f).coerceAtLeast(0f),
        )
        if (borderSize.width > 0f && borderSize.height > 0f) {
            drawRect(
                color = color,
                topLeft = Offset(insetPx, insetPx),
                size = borderSize,
                style = Stroke(width = strokeWidth.toPx()),
            )
        }

        drawKeyLabel(color = color, insetPx = insetPx)
    }

    private fun ContentDrawScope.drawKeyLabel(color: Color, insetPx: Float) {
        val key = key ?: return
        val label = "$key ×$recomposeCount"
        val layout = measureLabel(label) ?: return

        val padX = KeyLabelPaddingX.toPx()
        val padY = KeyLabelPaddingY.toPx()
        val gap = KeyLabelGap.toPx()
        val width = layout.size.width + padX * 2
        val height = layout.size.height + padY * 2

        val left = insetPx + KeyLabelMargin.toPx()
        val bottom = size.height - insetPx - KeyLabelMargin.toPx()

        // 与其它 highlighter 的标签自动避让：0 行最靠近左下角，逐行往上试
        val labelRect = RecomposeHighlighterKeys.place(id = this@RecomposeHighlighterNode) { row ->
            Rect(
                offset = Offset(left, bottom - height - row * (height + gap)),
                size = Size(width, height),
            )
        }

        drawRoundRect(
            color = color,
            topLeft = labelRect.topLeft,
            size = labelRect.size,
            cornerRadius = CornerRadius(KeyLabelCorner.toPx()),
        )
        drawText(
            textLayoutResult = layout,
            topLeft = Offset(labelRect.left + padX, labelRect.top + padY),
        )
    }

    private fun measureLabel(label: String): TextLayoutResult? {
        measuredLayout?.let { if (measuredLabel == label) return it }

        val layout = textMeasurer.measure(
            text = AnnotatedString(label),
            style = KeyLabelStyle,
        )
        measuredLabel = label
        measuredLayout = layout
        return layout
    }

    override fun onDetach() {
        // coroutineScope 会随节点 detach 一起取消，这里只是把意图写明
        resetJob?.cancel()
        RecomposeHighlighterKeys.release(this)
    }
}
