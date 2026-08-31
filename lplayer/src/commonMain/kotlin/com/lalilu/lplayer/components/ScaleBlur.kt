package com.lalilu.lplayer.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * 在较低分辨率的图层上执行模糊，再将图层放大回原始尺寸。
 *
 * [scale] 表示模糊图层相对最终显示尺寸的比例，取值范围为 `(0, 1]`。例如 `0.5f`
 * 会让图层的宽高各缩小一半，使模糊需要处理的像素数量约为原来的四分之一。
 * 模糊半径会同步按比例缩小，因此图层放大后的视觉半径仍与 [radius] 一致。
 *
 * `scale = 1f` 时直接使用 Compose 原生 [blur]，可作为画质和性能对照。该 Modifier
 * 会改变子内容的测量分辨率，适合使用在尺寸由父布局明确约束的图片或背景上。
 */
fun Modifier.scaleBlur(
    radius: Dp,
    scale: Float = 1f,
): Modifier {
    require(scale > 0f && scale <= 1f) {
        "scaleBlur 的 scale 必须在 (0, 1] 范围内，当前值为 $scale"
    }

    if (radius <= 0.dp) return this
    if (scale == 1f) {
        return blur(radius = radius, edgeTreatment = BlurredEdgeTreatment.Unbounded)
    }

    return this
        .layout { measurable, constraints ->
            val scaledConstraints = constraints.scaledBy(scale)
            val placeable = measurable.measure(scaledConstraints)
            val width = placeable.width.restoredBy(
                scale = scale,
                min = constraints.minWidth,
                max = constraints.maxWidth,
            )
            val height = placeable.height.restoredBy(
                scale = scale,
                min = constraints.minHeight,
                max = constraints.maxHeight,
            )

            layout(width, height) {
                placeable.place(0, 0)
            }
        }
        .graphicsLayer {
            // 图层放大后，缩小过的模糊半径也会被同比放大回调用方指定的视觉尺寸。
            scaleX = 1f / scale
            scaleY = 1f / scale
            transformOrigin = TransformOrigin(0f, 0f)
            renderEffect = BlurEffect(
                radiusX = radius.toPx() * scale,
                radiusY = radius.toPx() * scale,
                // 与原先 BlurredEdgeTreatment.Unbounded 的采样策略保持一致。
                edgeTreatment = TileMode.Decal,
            )
        }
}

private fun Constraints.scaledBy(scale: Float): Constraints {
    fun Int.scaledConstraint(): Int = when (this) {
        Constraints.Infinity -> Constraints.Infinity
        0 -> 0
        else -> (this * scale).roundToInt().coerceAtLeast(1)
    }

    val maxWidth = maxWidth.scaledConstraint()
    val maxHeight = maxHeight.scaledConstraint()
    return Constraints(
        minWidth = minWidth.scaledConstraint().coerceAtMost(maxWidth),
        maxWidth = maxWidth,
        minHeight = minHeight.scaledConstraint().coerceAtMost(maxHeight),
        maxHeight = maxHeight,
    )
}

private fun Int.restoredBy(scale: Float, min: Int, max: Int): Int {
    val restored = (this / scale).roundToInt().coerceAtLeast(min)
    return if (max == Constraints.Infinity) restored else restored.coerceAtMost(max)
}
