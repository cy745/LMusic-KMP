package com.lalilu.lhistory

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.request.Options
import com.lalilu.adaptiveValue
import com.lalilu.component.LazyGridContent
import com.lalilu.component.divider
import com.lalilu.lhistory.viewmodel.HistoryVM
import com.lalilu.lmedia.data.LMedia
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.LItem
import com.lalilu.lplayer.action.PlayerAction
import com.lalilu.navigation.AppRouter
import com.lalilu.slot
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single


@Named("history_panel")
@Single
class HistoryPanel : LazyGridContent {
    @OptIn(ExperimentalMaterialApi::class)
    @Composable
    override fun register(): LazyGridScope.() -> Unit {
        val scope = rememberCoroutineScope()
        val context = LocalPlatformContext.current
        val vm = koinViewModel<HistoryVM>()

        val columnsValue = adaptiveValue(
            compact = { 1 },
            medium = { 2 },
            expanded = { 3 }
        )

        return fun LazyGridScope.() {
            item(span = { GridItemSpan(maxLineSpan) }) {
                slot(
                    modifier = Modifier.fillMaxWidth(),
                    key = "recommend_title",
                ) {
                    "title" reg "历史播放"
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
                items = { vm.recentSongsFlow.value },
                key = { it.idValue() },
                contentType = { "HISTORY_ITEM" },
                span = { columnsValue.value }
            ) {
                slot(modifier = Modifier.fillMaxWidth(), key = "audio_item_card") {
                    "title" reg it.titleValue()
                    "subtitle" reg it.subtitleValue()
                    "imageData" reg it
                    "onPlay" reg {
                        scope.launch {
                            PlayerAction.UpdateList(
                                ids = LMedia.instance.get<LAudio>().map(LItem::idValue),
                                id = it.idValue(),
                                start = true
                            ).action()
                        }
                    }
                    "onNavigateToDetail" reg {
                        val imageLoader = SingletonImageLoader.get(context)
                        val coverMemoryKey = imageLoader.components.key(it, Options(context))

                        AppRouter.route("/song/detail")
                            .with("mediaId", it.idValue())
                            .with("song", it)
                            .with("coverCacheKey", coverMemoryKey)
                            .jump()
                    }
                }
            }

            divider()
        }
    }
}