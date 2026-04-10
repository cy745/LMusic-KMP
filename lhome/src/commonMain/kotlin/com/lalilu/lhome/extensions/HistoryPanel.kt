package com.lalilu.lhome.extensions

import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.lalilu.lhome.component.AudioItemCard
import com.lalilu.lhome.component.RecommendTitle
import com.lalilu.lhome.viewmodel.HistoryVM
import com.lalilu.lmedia.data.LMedia
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.LItem
import com.lalilu.lplayer.action.PlayerAction
import com.lalilu.navigation.AppRouter
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

object HistoryPanel : LazyGridContent {
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
                RecommendTitle(title = "历史播放") {
                    FilterChip(
                        selected = true,
                        shape = RoundedCornerShape(50),
                        onClick = {
                            AppRouter.route("/pages/songs")
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

            gridItems(
                items = { vm.recentSongsFlow.value },
                key = { it.idValue() },
                contentType = { "HISTORY_ITEM" },
                span = { columnsValue.value }
            ) {
                AudioItemCard(
                    modifier = Modifier,
                    title = it.titleValue(),
                    subtitle = it.subtitleValue(),
                    imageData = it,
                    onPlay = {
                        scope.launch {
                            PlayerAction.UpdateList(
                                ids = LMedia.instance.get<LAudio>().map(LItem::idValue),
                                id = it.idValue(),
                                start = true
                            ).action()
                        }
                    },
                    onNavigateToDetail = {
                        val imageLoader = SingletonImageLoader.get(context)
                        val coverMemoryKey = imageLoader.components.key(it, Options(context))

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