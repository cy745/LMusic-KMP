package com.lalilu.lhistory

import androidx.compose.animation.animateBounds
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.lalilu.component.AdaptiveLookaheadScope
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lalilu.Slot
import com.lalilu.adaptiveValue
import com.lalilu.component.LazyGridContent
import com.lalilu.lhistory.viewmodel.HistoryVM
import com.lalilu.lmedia.component.AudioItemCard
import com.lalilu.lmedia.isAudioPlayable
import com.lalilu.lplayer.action.PlayerAction
import com.lalilu.navigation.AppRouter
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single


@Named("history_panel")
@Single
class HistoryPanel : LazyGridContent {
    @OptIn(ExperimentalGridApi::class)
    @Composable
    override fun register(): LazyGridScope.() -> Unit {
        val scope = rememberCoroutineScope()
        val vm = koinViewModel<HistoryVM>()

        return fun LazyGridScope.() {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Slot(
                    modifier = Modifier.fillMaxWidth(),
                    key = "recommend_title",
                ) {
                    "title" reg "最近播放"
                    "extraContent" reg @Composable {
                        FilterChip(
                            selected = true,
                            shape = RoundedCornerShape(50),
                            onClick = {
                                AppRouter.route("/pages/history")
                                    .jump()
                            },
                            label = {
                                Text(
                                    text = "历史播放",
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        )
                    }
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                val items by vm.historyState

                if (items.isEmpty()) {
                    com.lalilu.component.LibraryQueryContent(vm.historyState) {
                        com.lalilu.component.LibraryEmptyState("还没有播放记录", "播放一首喜欢的歌，从这里继续聆听")
                    }
                    return@item
                }

                val columnsValue = adaptiveValue(
                    compact = { 1 },
                    medium = { 2 },
                    expanded = { 3 }
                )

                AdaptiveLookaheadScope lookaheadScope@{
                    Grid(
                        modifier = Modifier.fillMaxWidth()
                            .animateBounds(this@lookaheadScope),
                        config = { repeat(columnsValue.value) { column(1.fr) } }
                    ) {
                        items.forEach { audio ->
                            key(audio.playbackId) {
                                AudioItemCard(
                                    modifier = Modifier.fillMaxWidth()
                                        .animateBounds(this@lookaheadScope),
                                    sharedMapPrefix = "history_panel",
                                    id = audio.playbackId,
                                    title = audio.title,
                                    subtitle = audio.subtitle,
                                    imageData = audio,
                                    enabled = isAudioPlayable(audio),
                                    onPlay = {
                                        vm.getHistoryPlayedIds { list ->
                                            scope.launch {
                                                PlayerAction.UpdateList(
                                                    ids = list,
                                                    id = audio.playbackId,
                                                    start = true
                                                ).action()
                                            }
                                        }
                                    },
                                    onNavigateToDetail = { sharedMap ->

                                        AppRouter.route("/song/detail")
                                            .with("mediaId", audio.playbackId)
                                            .with("song", audio)
                                            .with("sharedMap", sharedMap)
                                            .jump()
                                    }
                                )
                            }
                        }
                    }
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
