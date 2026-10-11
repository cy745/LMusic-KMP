package com.lalilu.lplayer.components

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import kotlin.math.ceil

/**
 * 将内容录制到较低分辨率的离屏图层中执行模糊，再放大到原始显示尺寸。
 *
 * [scale] 表示离屏缓冲区相对最终显示尺寸的比例，取值范围为 `(0, 1]`。例如 `0.5f`
 * 会让缓冲区的宽高各缩小一半，使模糊需要处理的像素数量约为原来的四分之一。
 * 模糊半径会按实际采样比例同步缩小，因此放大后的视觉半径仍与 `radius()` 一致。
 *
 * `scale < 1f` 时不会改变子内容的测量尺寸，而是通过 `GraphicsLayer.record` 在绘制阶段
 * 完成降采样。离屏图层会在 Modifier 存续期间持续复用，动态修改半径不会反复切换
 * 布局结构或重新创建图层，可减少模糊开始和结束时的闪烁。
 *
 * [radius] 是**函数**：它在绘制期求值，因此模糊半径可以直接由动画状态驱动
 * （传 `{ someState.value }` 即可），只会触发重绘，不会让调用方逐帧重组。
 * 返回 `0.dp` 时直接按原分辨率绘制、不经过离屏图层（省一次合成；画质与走图层一致）。
 *
 * `scale = 1f` 时改用 `graphicsLayer` 的原生模糊，可作为画质和性能对照。
 */
fun Modifier.scaleBlur(
    radius: () -> Dp,
    scale: Float = 1f,
): Modifier {
    require(scale > 0f && scale <= 1f) {
        "scaleBlur 的 scale 必须在 (0, 1] 范围内，当前值为 $scale"
    }

    if (scale == 1f) {
        // graphicsLayer 的块在图层阶段执行，读值同样不会触发重组
        return graphicsLayer {
            val radiusPx = radius().toPx().coerceAtLeast(0f)
            renderEffect = if (radiusPx > 0f) {
                BlurEffect(radiusX = radiusPx, radiusY = radiusPx, edgeTreatment = TileMode.Clamp)
            } else {
                null
            }
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

                    val radiusPx = currentRadius.value().toPx().coerceAtLeast(0f)
                    // 半径为 0 时直接按原分辨率绘制，完全不经过离屏图层：
                    // 省掉一次 record + drawLayer（实测画质与走图层一致，这里纯粹是省一次合成）。
                    if (radiusPx <= 0f) {
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

                    // 3σ 边缘外扩：Skia 的 BlurEffect 会在记录区域外采样（Clamp 语义），
                    // 若内容紧贴 layer 边界，外采样会命中透明区域，形成边缘暗带，
                    // 在 radius 动态变化时表现为上下边缘闪烁（iOS 上尤其明显）。
                    // 内容按 expanded 尺寸放大绘制铺满整个层（无透明边缘），
                    // 模糊后整体平移对齐，超出的外扩部分由外层 clipToBounds 裁回。
                    val sigmaX = radiusPx * contentScaleX
                    val sigmaY = radiusPx * contentScaleY
                    val padX = ceil(sigmaX * 3f).toInt().coerceAtMost(256)
                    val padY = ceil(sigmaY * 3f).toInt().coerceAtMost(256)
                    val expandedSize = IntSize(
                        width = layerSize.width + padX * 2,
                        height = layerSize.height + padY * 2,
                    )
                    val drawScaleX = expandedSize.width / size.width
                    val drawScaleY = expandedSize.height / size.height

                    layer.apply {
                        scaleX = 1f / contentScaleX
                        scaleY = 1f / contentScaleY
                        renderEffect = BlurEffect(
                            radiusX = sigmaX,
                            radiusY = sigmaY,
                            edgeTreatment = TileMode.Clamp,
                        )

                        record(size = expandedSize) {
                            // 1) 外扩层：内容按 expanded 尺寸放大铺满整个层，
                            //    仅用于让 blur 采样落在有效内容上（显示时被裁掉）。
                            scale(
                                scaleX = drawScaleX,
                                scaleY = drawScaleY,
                                pivot = Offset.Zero,
                            ) {
                                this@onDrawWithContent.drawContent()
                            }
                            // 2) 内容层：原始比例、居中绘制，与显示尺寸一一对应，
                            //    覆盖在放大层之上——显示区域内容既不放大也不裁切。
                            translate(padX.toFloat(), padY.toFloat()) {
                                scale(
                                    scaleX = contentScaleX,
                                    scaleY = contentScaleY,
                                    pivot = Offset.Zero,
                                ) {
                                    this@onDrawWithContent.drawContent()
                                }
                            }
                        }
                    }

                    // expanded 层中央与原始内容区域对齐：绘制时反向平移外扩差值。
                    val offsetX = (expandedSize.width - layerSize.width) / 2f / contentScaleX
                    val offsetY = (expandedSize.height - layerSize.height) / 2f / contentScaleY
                    withTransform({
                        translate(
                            left = -offsetX,
                            top = -offsetY,
                        )
                    }) {
                        drawLayer(layer)
                    }
                }
            }
        }
    }
}
