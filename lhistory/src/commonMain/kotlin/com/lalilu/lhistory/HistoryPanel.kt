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
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.LocalPlatformContext
import com.lalilu.Slot
import com.lalilu.adaptiveValue
import com.lalilu.component.LazyGridContent
import com.lalilu.extensions.retrieveCacheKey
import com.lalilu.lhistory.viewmodel.HistoryVM
import com.lalilu.lmedia.component.AudioItemCard
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
        val context = LocalPlatformContext.current
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
                    Text(
                        modifier = Modifier.fillMaxWidth()
                            .padding(vertical = 32.dp),
                        text = "暂无数据",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(0.8f)
                    )
                    return@item
                }

                val columnsValue = adaptiveValue(
                    compact = { 1 },
                    medium = { 2 },
                    expanded = { 3 }
                )

                LookaheadScope lookaheadScope@{
                    Grid(
                        modifier = Modifier.fillMaxWidth()
                            .animateBounds(this@lookaheadScope),
                        config = { repeat(columnsValue.value) { column(1.fr) } }
                    ) {
                        items.forEach { audio ->
                            key(audio.idValue()) {
                                AudioItemCard(
                                    modifier = Modifier.fillMaxWidth()
                                        .animateBounds(this@lookaheadScope),
                                    sharedMapPrefix = "history_panel",
                                    id = audio.idValue(),
                                    title = audio.titleValue(),
                                    subtitle = audio.subtitleValue(),
                                    imageData = audio,
                                    onPlay = {
                                        vm.getHistoryPlayedIds { list ->
                                            scope.launch {
                                                PlayerAction.UpdateList(
                                                    ids = list,
                                                    id = audio.idValue(),
                                                    start = true
                                                ).action()
                                            }
                                        }
                                    },
                                    onNavigateToDetail = { sharedMap ->
                                        val coverMemoryKey = context.retrieveCacheKey(audio)

                                        AppRouter.route("/song/detail")
                                            .with("mediaId", audio.idValue())
                                            .with("song", audio)
                                            .with("coverCacheKey", coverMemoryKey)
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