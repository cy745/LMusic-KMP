package com.lalilu.lhistory

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import coil3.compose.LocalPlatformContext
import com.lalilu.Slot
import com.lalilu.adaptiveValue
import com.lalilu.component.LazyGridContent
import com.lalilu.component.divider
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
    @Composable
    override fun register(): LazyGridScope.() -> Unit {
        val scope = rememberCoroutineScope()
        val context = LocalPlatformContext.current
        val vm = koinViewModel<HistoryVM>()
        val items by vm.historyState

        val columnsValue = adaptiveValue(
            compact = { 1 },
            medium = { 2 },
            expanded = { 3 }
        )

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

            gridItems(
                items = { items },
                key = { it.idValue() },
                contentType = { "HISTORY_ITEM" },
                span = { columnsValue.value }
            ) {
                AudioItemCard(
                    modifier = Modifier.fillMaxWidth(),
                    title = it.titleValue(),
                    subtitle = it.subtitleValue(),
                    imageData = it,
                    onPlay = {
                        vm.getHistoryPlayedIds { list ->
                            scope.launch {
                                PlayerAction.UpdateList(
                                    ids = list,
                                    id = it.idValue(),
                                    start = true
                                ).action()
                            }
                        }
                    },
                    onNavigateToDetail = {
                        val coverMemoryKey = context.retrieveCacheKey(it)

                        AppRouter.route("/song/detail")
                            .with("mediaId", it.idValue())
                            .with("song", it)
                            .with("coverCacheKey", coverMemoryKey)
                            .jump()
                    }
                )
            }

            divider()
        }
    }
}