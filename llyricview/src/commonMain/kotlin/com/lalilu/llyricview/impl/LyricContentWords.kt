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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lalilu.LocalFontFamily
import com.lalilu.LocalLyricFontFamily
import com.lalilu.llyric.LyricItem
import com.lalilu.llyric.findPlayingIndexForWords
import com.lalilu.llyricview.LyricContext
import com.lalilu.llyricview.LyricItemLayout
import com.lalilu.llyricview.LyricSettings
import com.lalilu.llyricview.utils.*
import kotlin.math.abs


private val DEFAULT_GRADIENT_GAP = 48.dp

object LyricWordsContent : LyricItemLayout<LyricItem.WordsLyric> {

    @Composable
    override fun content(
        index: Int,
        item: LyricItem.WordsLyric,
        modifier: Modifier,
        settings: LyricSettings,
        context: LyricContext,
        onClick: (() -> Unit)?,
        onLongClick: (() -> Unit)?
    ) {
        LyricContentWords(
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
fun LyricContentWords(
    index: Int,
    lyric: LyricItem.WordsLyric,
    modifier: Modifier = Modifier,
    settings: LyricSettings,
    context: LyricContext,
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)?,
) {
    val isCurrent = context.currentIndex() == index
    val fullSentence = remember { lyric.getSentenceContent() }
    val scaleValue = remember {
        derivedStateOf {
            when {
                context.currentIndex() == index -> settings.scaleRange.endInclusive
                context.currentTime() in lyric.startTime..lyric.endTime -> 0.95f
                else -> settings.scaleRange.start
            }
        }
    }

    val scale = animateFloatAsState(
        targetValue = scaleValue.value,
        visibilityThreshold = 0.001f,
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
        if (lyric.translation.isEmpty()) return@remember false
        if (lyric.translation.firstOrNull()?.content?.isBlank() == true) return@remember false
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
        val textResult = remember { mutableStateOf<TextLayoutResult?>(null) }
        val tempPath = remember { Path() }

        Text(
            modifier = modifier.fillMaxWidth()
                .drawWithContent {
                    val now = context.currentTime()
                    val wordIndex = lyric.words.findPlayingIndexForWords(now)
                    val word = lyric.words.getOrNull(wordIndex)

                    // 获取某一词的播放进度
                    var progress = normalized(
                        start = word?.startTime ?: 0,
                        end = word?.endTime ?: 0,
                        current = now
                    )

                    // 若当前词已经播放完毕，则进度固定为1
                    if ((word?.endTime ?: Long.MAX_VALUE) < now) {
                        progress = Float.MAX_VALUE
                    }

                    withSaveLayer {
                        this@drawWithContent.drawContent()
                        val color = if (progress == Float.MAX_VALUE) Color.White else Color(0x80FFFFFF)

                        drawRect(
                            topLeft = Offset.Zero,
                            size = size,
                            color = color,
                            blendMode = BlendMode.SrcIn
                        )
                    }

                    if (progress > 0f) {
                        val textLayout = textResult.value ?: return@drawWithContent
                        val offset = lyric.words.take(wordIndex)
                            .sumOf { it.content.length }

                        tempPath.reset()
                        val (path, rect, position) = textLayout.getPathForProgress(
                            path = tempPath,
                            progress = progress,
                            offset = offset,
                            length = word?.content?.length
                        )

                        val lineProgress = if (progress >= 0.99f) 1f else {
                            normalized(
                                start = rect.left,
                                end = rect.right,
                                current = position
                            )
                        }

                        val offsetForProgress = DEFAULT_GRADIENT_GAP.toPx() * (1f - lineProgress)
                        val leftBound = position - offsetForProgress
                        val rightBound = (position + DEFAULT_GRADIENT_GAP.toPx() - offsetForProgress)
                        val rectForGradient = rect.copy(left = leftBound, right = rightBound)

                        // 向右扩展一段距离，为渐变预留足够的空间
                        path.addRect(
                            rectForGradient.copy(right = rectForGradient.right.coerceAtMost(rect.right))
                        )

                        clipPath(path) {
                            withSaveLayer {
                                this@drawWithContent.drawContent()

                                drawRect(
                                    topLeft = Offset.Zero,
                                    size = size,
                                    color = Color.White,
                                    blendMode = BlendMode.SrcIn
                                )

                                val gradient = Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.Black,
                                        Color.Black.copy(0.4f),
                                        Color.Transparent
                                    ),
                                    startX = leftBound,
                                    endX = rightBound
                                )

                                clipRect(rect = rect) {
                                    drawRect(
                                        rect = rectForGradient,
                                        brush = gradient,
                                        blendMode = BlendMode.DstIn
                                    )
                                }
                            }
                        }
                    }
            },
            text = fullSentence,
            style = settings.mainTextStyle.copy(
                fontFamily = LocalLyricFontFamily.current ?: LocalFontFamily.current
            ),
            onTextLayout = { textResult.value = it },
        )

        if (lyric.translation.isNotEmpty()) {
            AnimatedVisibility(
                modifier = Modifier.fillMaxWidth(),
                visible = translationVisible,
                enter = fadeIn() + expandVertically(clip = false),
                exit = fadeOut() + shrinkVertically(clip = false)
            ) {
                Text(
                    text = lyric.translation[0].content,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = settings.gapSize),
                    style = settings.translationTextStyle.copy(
                        fontFamily = LocalLyricFontFamily.current ?: LocalFontFamily.current
                    ),
                    color = Color(0x80FFFFFF)
                )
            }
        }
    }
}
