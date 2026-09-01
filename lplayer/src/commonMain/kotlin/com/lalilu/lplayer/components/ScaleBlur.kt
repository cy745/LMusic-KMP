package com.lalilu.lplayer.components

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.ceil

/**
 * 将内容录制到较低分辨率的离屏图层中执行模糊，再放大到原始显示尺寸。
 *
 * [scale] 表示离屏缓冲区相对最终显示尺寸的比例，取值范围为 `(0, 1]`。例如 `0.5f`
 * 会让缓冲区的宽高各缩小一半，使模糊需要处理的像素数量约为原来的四分之一。
 * 模糊半径会按实际采样比例同步缩小，因此放大后的视觉半径仍与 [radius] 一致。
 *
 * `scale < 1f` 时不会改变子内容的测量尺寸，而是通过 `GraphicsLayer.record` 在绘制阶段
 * 完成降采样。离屏图层会在 Modifier 存续期间持续复用，动态修改 [radius] 不会反复切换
 * 布局结构或重新创建图层，可减少模糊开始和结束时的闪烁。
 *
 * `scale = 1f` 时直接使用 Compose 原生 [blur]，可作为画质和性能对照。
 */
fun Modifier.scaleBlur(
    radius: Dp,
    scale: Float = 1f,
): Modifier {
    require(scale > 0f && scale <= 1f) {
        "scaleBlur 的 scale 必须在 (0, 1] 范围内，当前值为 $scale"
    }

    if (scale == 1f) {
        return if (radius <= 0.dp) this else {
            blur(radius = radius, edgeTreatment = BlurredEdgeTreatment.Unbounded)
        }
    }

    return composed {
        val currentRadius = rememberUpdatedState(radius)
        val currentScale = rememberUpdatedState(scale)

        remember {
            Modifier.drawWithCache {
                val layer = obtainGraphicsLayer().apply {
                    compositingStrategy = CompositingStrategy.Offscreen
                    pivotOffset = Offset.Zero
                }

                onDrawWithContent {
                    if (size.width <= 0f || size.height <= 0f) {
                        drawContent()
                        return@onDrawWithContent
                    }

                    val requestedScale = currentScale.value
                    val layerSize = IntSize(
                        width = ceil(size.width * requestedScale).toInt().coerceAtLeast(1),
                        height = ceil(size.height * requestedScale).toInt().coerceAtLeast(1),
                    )
                    // 分别使用取整后的真实比例，避免放大后在右侧或底部留下亚像素缝隙。
                    val contentScaleX = layerSize.width / size.width
                    val contentScaleY = layerSize.height / size.height
                    val radiusPx = currentRadius.value.toPx().coerceAtLeast(0f)

                    layer.apply {
                        scaleX = 1f / contentScaleX
                        scaleY = 1f / contentScaleY
                        renderEffect = if (radiusPx > 0f) {
                            BlurEffect(
                                radiusX = radiusPx * contentScaleX,
                                radiusY = radiusPx * contentScaleY,
                                edgeTreatment = TileMode.Decal,
                            )
                        } else {
                            null
                        }

                        record(size = layerSize) {
                            scale(
                                scaleX = contentScaleX,
                                scaleY = contentScaleY,
                                pivot = Offset.Zero,
                            ) {
                                this@onDrawWithContent.drawContent()
                            }
                        }
                    }

                    drawLayer(layer)
                }
            }
        }
    }
}
