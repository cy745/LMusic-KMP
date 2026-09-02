package com.lalilu.llyricview

import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lalilu.common.kv.KVItem
import com.lalilu.extensions.ClassicBackHandler
import com.lalilu.extensions.ItemRecorder
import com.lalilu.extensions.rememberLazyListAnimateScroller
import com.lalilu.extensions.startRecord
import com.lalilu.llyric.LyricItem
import com.lalilu.llyric.findPlayingIndex
import com.lalilu.llyricview.impl.LyricContentNormal
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import org.koin.compose.koinInject
import org.koin.core.qualifier.named

@OptIn(FlowPreview::class)
@Composable
fun LyricLayout(
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    currentTime: () -> Long = { 0L },
    lyricEntry: State<List<LyricItem>> = remember { mutableStateOf(emptyList()) },
    screenConstraints: Constraints,
    isUserClickEnable: () -> Boolean = { false },
    isUserScrollEnable: () -> Boolean = { false },
    onPositionReset: () -> Unit = {},
    onItemClick: (LyricItem) -> Unit = {},
    onItemLongClick: (LyricItem) -> Unit = {},
) {
    val density = LocalDensity.current
    val settings: KVItem<LyricSettings> = koinInject(named("LyricSettings"))
    val textMeasurer = rememberTextMeasurer()
    val isUserScrolling = remember { mutableStateOf(isUserScrollEnable()) }
        .also { it.value = isUserScrollEnable() }
    val recorder = remember { ItemRecorder() }
    val scroller = rememberLazyListAnimateScroller(
        listState = listState,
        enableScrollAnimation = { !isUserScrolling.value },
        keys = { recorder.list().filterNotNull() }
    )

    val currentItemIndex = remember {
        derivedStateOf {
            val time = currentTime() + settings.value.timeOffset
            val lyricEntryList = lyricEntry.value

            lyricEntryList.findPlayingIndex(time)
        }
    }

    val currentItem: State<LyricItem?> = remember {
        derivedStateOf {
            currentItemIndex.value
                .takeIf { it != Int.MAX_VALUE }
                ?.let { lyricEntry.value[it] }
        }
    }

    ClassicBackHandler(
        enabled = isUserScrolling.value,
        onBack = {
            isUserScrolling.value = false
            currentItem.value?.key?.let(scroller::animateTo)
            onPositionReset()
        }
    )

    LaunchedEffect(Unit) {
        val (initialLyrics, initialIndex) = snapshotFlow {
            lyricEntry.value to currentItemIndex.value
        }.first { (lyrics, index) ->
            lyrics.isNotEmpty() && index != Int.MAX_VALUE
        }
        val targetIndex = initialIndex.coerceIn(initialLyrics.indices)

        listState.scrollToItem(targetIndex)

        // 完成恢复后才开始收集播放位置，避免恢复任务和自动跟随同时滚动同一个列表。
        snapshotFlow { currentItem.value }
            .collectLatest { item ->
                item ?: return@collectLatest
                scroller.animateTo(
                    key = item.key,
                    animationSpec = spring(
                        dampingRatio = settings.value.scrollSpringDampingRatio,
                        stiffness = settings.value.scrollSpringStiffness,
                        visibilityThreshold = 0.001f
                    )
                )
            }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { listState.isScrollInProgress to isUserScrollEnable() }
            .debounce(5000)
            .collectLatest { pair ->
                val (isDragging, isScrolling) = pair
                if (!isActive || isDragging || !isScrolling) return@collectLatest

                isUserScrolling.value = false
                currentItem.value?.key?.let(scroller::animateTo)
                onPositionReset()
            }
    }

    val context = remember {
        LyricContext(
            currentTime = { currentTime() + settings.value.timeOffset },
            currentIndex = { currentItemIndex.value },
            isUserScrolling = { isUserScrolling.value },
            screenConstraints = screenConstraints,
            textMeasurer = textMeasurer,
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val heightSplit = remember(screenConstraints) {
            density.run { screenConstraints.maxHeight.toDp() / 3f }
        }

        LazyColumn(
            state = listState,
            modifier = modifier
                .fillMaxSize(),
//                .edgeTransparent(top = heightSplit, bottom = heightSplit),
            userScrollEnabled = true,
            contentPadding = PaddingValues(
                top = heightSplit,
                bottom = heightSplit * 2f
            )
        ) {
            startRecord(recorder) {
                if (lyricEntry.value.isEmpty()) {
                    item(key = "EMPTY_TIPS") {
                        val item = remember {
                            LyricItem.NormalLyric(
                                key = "0",
                                content = "暂无歌词",
                                time = 0L
                            )
                        }

                        LyricContentNormal(
                            lyric = item,
                            index = context.currentIndex(),
                            modifier = Modifier,
                            settings = settings.value,
                            context = context,
                            onLongClick = { if (isUserClickEnable()) onItemLongClick(item) },
                            onClick = { }
                        )
                    }
                } else {
                    itemsIndexed(
                        items = lyricEntry.value,
                        key = { _, item -> item.key },
                        contentType = { _, _ -> LyricItem::class }
                    ) { index, item ->
                        LyricItemLayout.get(item)?.content(
                            item = item,
                            index = index,
                            modifier = Modifier,
                            settings = settings.value,
                            context = context,
                            onLongClick = { if (isUserClickEnable()) onItemLongClick(item) },
                            onClick = { if (isUserClickEnable()) onItemClick(item) }
                        )
                    }
                }
            }
        }

        val contentColor = remember { Color(0xFFFFFFFF) }
        val colors = ButtonDefaults.textButtonColors(
            containerColor = contentColor.copy(alpha = 0.15f),
            contentColor = contentColor
        )

        AnimatedVisibility(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 120.dp)
                .fillMaxWidth(),
            enter = fadeIn() + slideIn { IntOffset(0, 100) },
            exit = fadeOut() + slideOut { IntOffset(0, 100) },
            visible = isUserScrolling.value
        ) {
            TextButton(
                modifier = Modifier.wrapContentWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = colors,
                onClick = {
                    isUserScrolling.value = false
                    currentItem.value?.key?.let(scroller::animateTo)
                    onPositionReset()
                }
            ) {
                Text(
                    text = "退出歌词滚动模式",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
