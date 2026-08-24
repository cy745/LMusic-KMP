package com.lalilu.lsearch.component

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lalilu.lsearch.viewmodel.SearchRecommendation
import com.lalilu.lsearch.viewmodel.SearchRecommendationCandidates
import kotlin.random.Random

private const val RECOMMENDATION_CHANGE_INTERVAL_MILLIS = 10_000
private val RECOMMENDATION_MAX_WIDTH = 200.dp

/**
 * 空关键词时展示的推荐词区域。
 *
 * 初始内容按歌手、专辑、歌曲 3:3:2 生成并打乱。组件进入组合后，每轮随机选择一个胶囊，
 * 用 10 秒线性填充背景；填满后从同类型候选中替换关键词，再开始下一轮。定时循环由
 * [LaunchedEffect] 托管，页面离开组合或候选池变化时会自动取消并重建。
 */
@Composable
internal fun SearchKeywordRecommendations(
    modifier: Modifier = Modifier,
    title: String,
    candidates: SearchRecommendationCandidates,
    onKeywordClick: (String) -> Unit,
) {
    var recommendations by remember { mutableStateOf(emptyList<SearchRecommendation>()) }
    var progressingIndex by remember { mutableIntStateOf(-1) }
    val progress = remember { Animatable(0f) }

    LaunchedEffect(candidates) {
        recommendations = candidates.initialRecommendations()
        progressingIndex = -1
        progress.snapTo(0f)

        while (recommendations.isNotEmpty()) {
            // 只从确实存在同类型替代词的胶囊中选择，保证每轮倒计时结束都会发生内容变化。
            val replacementOptions = recommendations.mapIndexedNotNull { index, current ->
                candidates.replacementFor(
                    current = current,
                    displayed = recommendations,
                )?.let { replacement -> index to replacement }
            }
            if (replacementOptions.isEmpty()) break

            val (index, replacement) = replacementOptions.random(Random.Default)
            progressingIndex = index
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = RECOMMENDATION_CHANGE_INTERVAL_MILLIS,
                    easing = LinearEasing,
                ),
            )

            val currentItems = recommendations
            if (index !in currentItems.indices) continue

            recommendations = currentItems.toMutableList().also { items ->
                items[index] = replacement
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (recommendations.isNotEmpty()) {
            FlowRow(
                // Lookahead 先按新关键词宽度完成一次目标布局，再由 animateBounds 将容器高度、
                // 胶囊尺寸和位置从旧布局连续过渡过去，避免换行时直接跳到另一行。
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .animateContentSize(animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy)),
                horizontalArrangement = Arrangement.spacedBy(
                    space = 8.dp,
                    alignment = Alignment.CenterHorizontally,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LookaheadScope lookaheadScope@{
                    recommendations.forEachIndexed { index, recommendation ->
                        key(index) {
                            SearchKeywordChip(
                                modifier = Modifier.animateBounds(this@lookaheadScope),
                                keyword = recommendation.keyword,
                                progress = { if (progressingIndex == index) progress.value else 0f },
                                onClick = { onKeywordClick(recommendation.keyword) },
                            )
                        }
                    }
                }
            }
        }

        Text(
            text = title,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** 单个推荐词胶囊。背景填充只参与绘制，不触发重新测量。 */
@Composable
private fun SearchKeywordChip(
    modifier: Modifier = Modifier,
    keyword: String,
    progress: () -> Float,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(percent = 50)
    val progressColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)

    Box(
        modifier = modifier
            .widthIn(max = RECOMMENDATION_MAX_WIDTH)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // matchParentSize 不参与父级测量；从左侧缩放即可得到随倒计时增长的进度背景。
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0.5f)
                    scaleX = progress().coerceIn(0f, 1f)
                }
                .background(progressColor)
        )

        BlurFadeTransition(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            item = { keyword },
        ) { animatedKeyword ->
            Text(
                modifier = Modifier.widthIn(max = RECOMMENDATION_MAX_WIDTH - 32.dp),
                text = animatedKeyword,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/**
 * 文本切换时同时执行淡入淡出和 blur 变化。
 *
 * 与全宽内容版本不同，这里不使用 `fillMaxWidth`，让 [AnimatedContent] 根据新旧文本自由改变
 * 宽度；外层的 `LookaheadScope + animateBounds` 会预先计算新宽度及换行后的最终位置，
 * 再连续过渡胶囊的尺寸和坐标，并限制最终最大宽度为 200.dp。
 */
@Composable
private fun <T> BlurFadeTransition(
    modifier: Modifier = Modifier,
    maxBlurDp: Dp = 25.dp,
    item: () -> T,
    content: @Composable (T) -> Unit,
) {
    AnimatedContent(
        modifier = Modifier,
        transitionSpec = {
            fadeIn(spring(stiffness = Spring.StiffnessLow)) togetherWith
                    fadeOut(spring(stiffness = Spring.StiffnessLow))
        },
        contentAlignment = Alignment.Center,
        targetState = item(),
        label = "SearchRecommendationBlurFade",
    ) { animatedItem ->
        val blurValue = transition.animateDp(
            transitionSpec = { spring(stiffness = Spring.StiffnessLow) },
            label = "SearchRecommendationBlur",
        ) { state ->
            when (state) {
                EnterExitState.Visible -> 0.dp
                EnterExitState.PreEnter -> maxBlurDp
                EnterExitState.PostExit -> maxBlurDp
            }
        }

        Box(
            modifier = modifier
                .wrapContentWidth()
                .blur(
                    radius = blurValue.value,
                    edgeTreatment = BlurredEdgeTreatment.Unbounded,
                ),
        ) {
            content(animatedItem)
        }
    }
}
