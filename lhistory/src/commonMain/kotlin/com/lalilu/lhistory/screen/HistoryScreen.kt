@file:OptIn(kotlin.time.ExperimentalTime::class)
package com.lalilu.lhistory.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.lalilu.RemixIcon
import com.lalilu.component.LibraryEmptyState
import com.lalilu.extensions.LocalToaster
import com.lalilu.extensions.PassThroughHelper
import com.lalilu.krouter.annotation.Destination
import com.lalilu.lhistory.component.*
import com.lalilu.lhistory.viewmodel.*
import com.lalilu.lmedia.domain.repository.AudioRepository
import com.lalilu.lmedia.domain.repository.getAudioByPlaybackId
import com.lalilu.lmedia.isAudioPlayable
import com.lalilu.lplayer.action.PlayerAction
import com.lalilu.navigation.*
import com.lalilu.navigation.smartbar.NavigatorHeader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.datetime.*
import kotlin.time.Instant
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Destination(router = ["/pages/history"])
data object HistoryScreen : Screen, ScreenInfoFactory, ScreenActionFactory {
    @Composable
    override fun provideScreenInfo(): ScreenInfo = remember {
        ScreenInfo(title = { "历史记录" }, icon = RemixIcon.System.historyLine)
    }

    @Composable
    override fun provideScreenActions(): List<ScreenAction> {
        val vm = koinViewModel<HistoryVM>()
        val scope = rememberCoroutineScope()
        val toaster = LocalToaster.current
        var clearing by remember { mutableStateOf(false) }
        return remember(vm, toaster, scope) {
            listOf(ScreenAction.Static(
                title = { "清除记录" }, subTitle = { "长按清除全部历史与收听统计" },
                icon = { RemixIcon.System.deleteBinLine }, color = { Color(0xFFF5381D) },
                longClick = { true }, onAction = { context ->
                    if (!clearing) {
                        clearing = true
                        scope.launch {
                            try { vm.clearHistory(); context.onDismiss(); toaster?.show("历史记录与收听统计已清除") }
                            catch (cancelled: CancellationException) { throw cancelled }
                            catch (error: Exception) { toaster?.show("清除失败，请重试") }
                            finally { clearing = false }
                        }
                    }
                },
            ))
        }
    }

    @Composable
    override fun Content() {
        val vm = koinViewModel<HistoryVM>()
        val entries = vm.pager.collectAsLazyPagingItems()
        val statistics by vm.statistics.collectAsStateWithLifecycle()
        val period by vm.period.collectAsStateWithLifecycle()
        val filter by vm.filter.collectAsStateWithLifecycle()
        val calendar by vm.calendar.collectAsStateWithLifecycle()
        val scope = rememberCoroutineScope()
        val audioRepo = koinInject<AudioRepository>()
        val navigationBar = WindowInsets.navigationBars.asPaddingValues()
        val smartBarHeight = PassThroughHelper.getValue("SmartBarHeight") { navigationBar.calculateBottomPadding() }
        val dayOf: (Long) -> LocalDate = { Instant.fromEpochMilliseconds(it).toLocalDateTime(calendar.zone).date }

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(
            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 16.dp,
            bottom = smartBarHeight() + 24.dp,
        )) {
            item("heading") { NavigatorHeader(Modifier.fillMaxWidth(), "历史记录", "听过的音乐，留下的时光") }
            item("statistics") {
                ListeningStatistics(statistics, period, calendar.today, { vm.period.value = it })
            }
            item("filter") {
                Row(Modifier.fillMaxWidth().padding(start = 10.dp, top = 24.dp, bottom = 8.dp)) {
                    HistoryFilter.entries.forEach { QuietChoice(it.label, filter == it, { vm.filter.value = it }) }
                }
            }
            if (entries.itemCount == 0 && entries.loadState.refresh is LoadState.Loading) {
                item("loading") { com.lalilu.component.LibraryLoadingState() }
            }
            if (entries.itemCount == 0 && entries.loadState.refresh is LoadState.NotLoading) {
                item("empty") {
                    LibraryEmptyState(
                        if (filter == HistoryFilter.All) "还没有收听记录" else "这段时间还没有收听记录",
                        if (filter == HistoryFilter.All) "播放一首喜欢的歌，让音乐留下足迹。" else "换个时间范围，看看之前听过的音乐。",
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 48.dp, bottom = 32.dp),
                        actionLabel = if (filter == HistoryFilter.All) "去曲库听歌" else "查看全部记录",
                        onAction = { if (filter == HistoryFilter.All) AppRouter.route("/home").jump() else vm.filter.value = HistoryFilter.All },
                    )
                }
            }
            items(entries.itemCount, key = entries.itemKey { it.id }) { index ->
                val entry = entries[index] ?: return@items
                val previous = if (index > 0) entries.peek(index - 1) else null
                Column(Modifier.animateItem()) {
                    if (previous == null || dayOf(previous.startTime) != dayOf(entry.startTime)) {
                        Text(historyDayLabel(dayOf(entry.startTime), calendar.today),
                            Modifier.padding(start = 20.dp, top = 20.dp, bottom = 10.dp),
                            fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f))
                    }
                    val audio by remember(entry.contentId) { audioRepo.getAudioByPlaybackId(entry.contentId) }
                        .collectAsStateWithLifecycle(null)
                    HistoryItemCard(title = entry.contentTitle, artist = audio?.subtitle.orEmpty(), imageData = audio,
                        startTime = entry.startTime, duration = entry.duration,
                        enabled = audio?.let { isAudioPlayable(it) } == true,
                        onLongClick = {
                            AppRouter.route("/song/detail").with("mediaId", entry.contentId).with("song", audio).jump()
                        },
                        onClick = {
                            vm.getHistoryPlayedIds { ids -> scope.launch {
                                PlayerAction.UpdateList(ids = ids, id = entry.contentId, start = true).action()
                            } }
                        })
                }
            }
            if (entries.loadState.refresh is LoadState.Error || entries.loadState.append is LoadState.Error) {
                item("retry") { LibraryEmptyState("记录暂时无法读取", "请稍后再试", actionLabel = "重试", onAction = { entries.retry() }) }
            }
            if (entries.itemCount > 0) item("note") {
                Text("实际开始播放计一次；暂停后恢复不重复计次。\n每日统计从本次版本开始积累，旧历史保留。",
                    Modifier.padding(20.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f))
            }
        }
    }
}
