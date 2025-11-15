package com.lalilu.extensions

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 创建一个修饰符，用于在组件边缘添加渐隐效果
 *
 * @param cutting 渐变分割段数，默认为10段，段数越多渐变越平滑
 * @param lengthDp 渐隐效果的长度，默认为100dp
 * @param alignmentX 水平方向的对齐方式，控制水平渐隐的位置
 * @param alignmentY 垂直方向的对齐方式，控制垂直渐隐的位置，默认为底部对齐
 * @param func 渐变函数，控制透明度变化曲线，默认为二次函数(it * it)
 */
fun Modifier.clipFade(
    cutting: Int = 10,
    lengthDp: Dp = 100.dp,
    alignmentX: Alignment.Horizontal? = null,
    alignmentY: Alignment.Vertical? = Alignment.Bottom,
    func: (x: Float) -> Float = { it * it }
) = composed {
    // 设置离屏渲染策略，确保绘制效果正确应用
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithCache {
            // 确定渐隐方向，优先使用水平方向设置
            val alignment = alignmentX ?: alignmentY
            // 将dp单位转换为像素单位
            val length = lengthDp.toPx()
            
            // 创建颜色停止点数组，用于构建渐变效果
            // 将0到1区间分为cutting段，每段计算对应的透明度值
            val colorStops = (0..cutting step 1)
                .map { it / cutting.toFloat() }
                .map { it to Color.Black.copy(alpha = func(it)) }
                .toTypedArray()

            // 根据对齐方式计算绘制参数
            val (startValue, topLeft, drawSize) = when (alignment) {
                // 垂直方向渐隐（顶部或底部）
                is Alignment.Vertical -> {
                    val startValue = size.height - length
                    val topLeft = Offset(x = 0.0F, y = startValue)
                    val drawSize = Size(width = size.width, height = length)

                    Triple(startValue, topLeft, drawSize)
                }

                // 水平方向渐隐（开始或结束）
                is Alignment.Horizontal -> {
                    val startValue = size.width - length
                    val topLeft = Offset(x = startValue, y = 0f)
                    val drawSize = Size(width = length, height = size.height)

                    Triple(startValue, topLeft, drawSize)
                }

                // 默认情况，不应用渐隐效果
                else -> Triple(0f, Offset.Zero, size)
            }

            // 实际绘制逻辑
            onDrawWithContent {
                // 先绘制原始内容
                drawContent()

                // 根据对齐方式应用对应的渐隐效果
                if (alignment is Alignment.Vertical) {
                    // 垂直方向渐隐，如果是顶部对齐则旋转180度
                    rotate(degrees = if (alignment == Alignment.Top) 180f else 0f) {
                        drawRect(
                            brush = Brush.verticalGradient(
                                colorStops = colorStops,
                                startY = startValue
                            ),
                            topLeft = topLeft,
                            size = drawSize,
                            blendMode = BlendMode.DstOut
                        )
                    }
                } else if (alignment is Alignment.Horizontal) {
                    // 水平方向渐隐，如果是起始对齐则旋转180度
                    rotate(degrees = if (alignment == Alignment.Start) 180f else 0f) {
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colorStops = colorStops,
                                startX = startValue
                            ),
                            topLeft = topLeft,
                            size = drawSize,
                            blendMode = BlendMode.DstOut
                        )
                    }
                }
            }
        }
}