package com.lalilu.extensions

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.*

private const val FADE_SAMPLE_COUNT = 16

/**
 * 让内容在指定边缘由完全透明自然过渡到完全不透明。
 *
 * 该效果只改变绘制结果，不影响组件的测量、布局和点击区域。[start] 与 [end] 会根据
 * 当前布局方向映射到实际的左右边缘；多个边缘同时启用时，其透明度遮罩会在角落自然叠加。
 *
 * 实现需要为被修饰内容创建离屏图层，因此更适合应用在列表视口等外层容器上，不建议为大量
 * 列表元素逐个添加。
 *
 * @param start 起始边缘的渐隐长度，非正数或未指定时不启用。
 * @param top 顶部边缘的渐隐长度，非正数或未指定时不启用。
 * @param end 结束边缘的渐隐长度，非正数或未指定时不启用。
 * @param bottom 底部边缘的渐隐长度，非正数或未指定时不启用。
 * @param easing 从内容区域向外缘消失时使用的透明度变化曲线。
 */
fun Modifier.fadeEdge(
    start: Dp = 0.dp,
    top: Dp = 0.dp,
    end: Dp = 0.dp,
    bottom: Dp = 0.dp,
    easing: Easing = FastOutSlowInEasing,
): Modifier {
    if (start.isDisabled && top.isDisabled && end.isDisabled && bottom.isDisabled) {
        return this
    }

    return graphicsLayer {
        // DstOut 必须限制在当前组件的离屏图层中，否则可能一并擦除其后方已经绘制的内容。
        compositingStrategy = CompositingStrategy.Offscreen
    }.drawWithCache {
        val topPx = toFadePx(top, size.height)
        val bottomPx = toFadePx(bottom, size.height)
        val startPx = toFadePx(start, size.width)
        val endPx = toFadePx(end, size.width)

        val leftPx: Float
        val rightPx: Float
        if (layoutDirection == LayoutDirection.Ltr) {
            leftPx = startPx
            rightPx = endPx
        } else {
            leftPx = endPx
            rightPx = startPx
        }

        // DstOut 使用黑色的 alpha 作为擦除强度：外缘为 1，内容侧为 0。
        val contentToEdgeStops = createFadeStops(easing = easing, reversed = false)
        val edgeToContentStops = createFadeStops(easing = easing, reversed = true)

        val topBrush = topPx.takeIf { it > 0f }?.let {
            Brush.verticalGradient(
                colorStops = edgeToContentStops,
                startY = 0f,
                endY = it,
            )
        }
        val bottomBrush = bottomPx.takeIf { it > 0f }?.let {
            Brush.verticalGradient(
                colorStops = contentToEdgeStops,
                startY = size.height - it,
                endY = size.height,
            )
        }
        val leftBrush = leftPx.takeIf { it > 0f }?.let {
            Brush.horizontalGradient(
                colorStops = edgeToContentStops,
                startX = 0f,
                endX = it,
            )
        }
        val rightBrush = rightPx.takeIf { it > 0f }?.let {
            Brush.horizontalGradient(
                colorStops = contentToEdgeStops,
                startX = size.width - it,
                endX = size.width,
            )
        }

        onDrawWithContent {
            drawContent()

            topBrush?.let { brush ->
                drawRect(
                    brush = brush,
                    size = Size(width = size.width, height = topPx),
                    blendMode = BlendMode.DstOut,
                )
            }
            bottomBrush?.let { brush ->
                drawRect(
                    brush = brush,
                    topLeft = Offset(x = 0f, y = size.height - bottomPx),
                    size = Size(width = size.width, height = bottomPx),
                    blendMode = BlendMode.DstOut,
                )
            }
            leftBrush?.let { brush ->
                drawRect(
                    brush = brush,
                    size = Size(width = leftPx, height = size.height),
                    blendMode = BlendMode.DstOut,
                )
            }
            rightBrush?.let { brush ->
                drawRect(
                    brush = brush,
                    topLeft = Offset(x = size.width - rightPx, y = 0f),
                    size = Size(width = rightPx, height = size.height),
                    blendMode = BlendMode.DstOut,
                )
            }
        }
    }
}

private val Dp.isDisabled: Boolean
    get() = isUnspecified || value <= 0f

private fun Density.toFadePx(value: Dp, maximum: Float): Float =
    if (value.isDisabled) 0f else value.toPx().coerceIn(0f, maximum)

private fun createFadeStops(
    easing: Easing,
    reversed: Boolean,
): Array<Pair<Float, Color>> = Array(FADE_SAMPLE_COUNT + 1) { index ->
    val fraction = index / FADE_SAMPLE_COUNT.toFloat()
    val easingFraction = if (reversed) 1f - fraction else fraction
    fraction to Color.Black.copy(
        alpha = easing.transform(easingFraction).coerceIn(0f, 1f),
    )
}
