package com.lalilu.llyricview.utils

import androidx.annotation.FloatRange
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope.Companion.DefaultBlendMode
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.withSaveLayer

internal fun DrawScope.clipRect(
    rect: Rect,
    clipOp: ClipOp = ClipOp.Intersect,
    block: DrawScope.() -> Unit
) {
    clipRect(rect.left, rect.top, rect.right, rect.bottom, clipOp, block)
}

internal fun DrawScope.drawRect(
    rect: Rect,
    brush: Brush,
    @FloatRange(from = 0.0, to = 1.0) alpha: Float = 1.0f,
    style: DrawStyle = Fill,
    colorFilter: ColorFilter? = null,
    blendMode: BlendMode = DefaultBlendMode,
) {
    drawRect(
        topLeft = rect.topLeft,
        size = rect.size,
        brush = brush,
        style = style,
        alpha = alpha,
        colorFilter = colorFilter,
        blendMode = blendMode
    )
}

private val EMPTY_PAINT = Paint()
internal inline fun DrawScope.withSaveLayer(crossinline block: DrawScope.() -> Unit) {
    drawContext.canvas.withSaveLayer(size.toRect(), EMPTY_PAINT) { block() }
}