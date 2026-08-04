package com.lalilu.llyricview.impl

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lalilu.LocalFontFamily
import com.lalilu.LocalLyricFontFamily
import com.lalilu.llyric.LyricItem
import com.lalilu.llyricview.LyricContext
import com.lalilu.llyricview.LyricItemLayout
import com.lalilu.llyricview.LyricSettings
import com.lalilu.llyricview.utils.blur
import kotlin.math.abs

object LyricNormalContent : LyricItemLayout<LyricItem.NormalLyric> {

    @Composable
    override fun content(
        index: Int,
        item: LyricItem.NormalLyric,
        modifier: Modifier,
        settings: LyricSettings,
        context: LyricContext,
        onClick: (() -> Unit)?,
        onLongClick: (() -> Unit)?
    ) {
        LyricContentNormal(
            index = index,
            lyric = item,
            modifier = modifier,
            settings = settings,
            context = context,
            onClick = onClick,
            onLongClick = onLongClick
        )
    }
}

@Composable
fun LyricContentNormal(
    index: Int,
    lyric: LyricItem.NormalLyric,
    modifier: Modifier = Modifier,
    settings: LyricSettings,
    context: LyricContext,
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)?,
) {
    val isCurrent = context.currentIndex() == index
    val color = animateColorAsState(
        targetValue = if (isCurrent) Color.White else Color(0x80FFFFFF),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = ""
    )
    val pivot = remember(settings.textAlign) {
        when (settings.textAlign) {
            TextAlign.End -> TransformOrigin.Center.copy(pivotFractionX = 1f)
            TextAlign.Center -> TransformOrigin.Center
            else -> TransformOrigin.Center.copy(pivotFractionX = 0f)
        }
    }
    val scale = animateFloatAsState(
        targetValue = if (isCurrent) settings.scaleRange.endInclusive
        else settings.scaleRange.start,
        visibilityThreshold = 0.001f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = ""
    )
    val blurRadius = remember(
        context.isUserScrolling(),
        context.currentIndex(),
        settings.blurEffectEnable
    ) {
        if (context.isUserScrolling()) return@remember 0.dp
        if (!settings.blurEffectEnable) return@remember 0.dp
        abs(index - context.currentIndex()).times(3).coerceAtMost(10).dp
    }
    val animateBlurRadius = animateDpAsState(
        targetValue = blurRadius,
        label = ""
    )
    val translationVisible = remember(settings, lyric, isCurrent, context.isUserScrolling()) {
        if (!settings.translationVisible) return@remember false
        if (lyric.translation.isNullOrBlank()) return@remember false
        if (!context.isUserScrolling()) {
            if (settings.onlyCurrentTranslationVisible && !isCurrent) return@remember false
        }

        return@remember true
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onLongClick = onLongClick, onClick = onClick ?: {})
            .padding(settings.containerPadding)
            .graphicsLayer {
                clip = false
                compositingStrategy = CompositingStrategy.Offscreen
                transformOrigin = pivot
                scaleX = scale.value
                scaleY = scaleX
                blur(animateBlurRadius.value)
            },
    ) {
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = lyric.content,
            style = settings.mainTextStyle.copy(
                fontFamily = LocalLyricFontFamily.current ?: LocalFontFamily.current
            ),
            color = color.value
        )

        lyric.translation?.let { translation ->
            AnimatedVisibility(
                modifier = Modifier.fillMaxWidth(),
                visible = translationVisible,
                enter = fadeIn() + expandVertically(clip = false),
                exit = fadeOut() + shrinkVertically(clip = false)
            ) {
                Text(
                    text = translation,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = settings.gapSize),
                    style = settings.translationTextStyle.copy(
                        fontFamily = LocalLyricFontFamily.current ?: LocalFontFamily.current
                    ),
                    color = color.value
                )
            }
        }
    }
}
