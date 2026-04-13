package com.lalilu.lhistory

import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import coil3.compose.LocalPlatformContext
import com.lalilu.adaptiveValue
import com.lalilu.component.LazyGridContent
import com.lalilu.component.divider
import com.lalilu.lhistory.viewmodel.HistoryVM
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single


@Named("history")
@Single
class HistoryPanel : LazyGridContent {
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
                Text(text = "历史播放")
            }
//            item(span = { GridItemSpan(maxLineSpan) }) {
//                RecommendTitle(title = "历史播放") {
//                    FilterChip(
//                        selected = true,
//                        shape = RoundedCornerShape(50),
//                        onClick = {
//                            AppRouter.route("/pages/songs")
//                                .jump()
//                        },
//                        label = {
//                            Text(
//                                text = "历史播放",
//                                style = MaterialTheme.typography.labelMedium,
//                            )
//                        }
//                    )
//                }
//            }

            gridItems(
                items = { vm.recentSongsFlow.value },
                key = { it.idValue() },
                contentType = { "HISTORY_ITEM" },
                span = { columnsValue.value }
            ) {
                Text(text = it.titleValue())
//                AudioItemCard(
//                    modifier = Modifier.Companion,
//                    title = it.titleValue(),
//                    subtitle = it.subtitleValue(),
//                    imageData = it,
//                    onPlay = {
//                        scope.launch {
//                            PlayerAction.UpdateList(
//                                ids = LMedia.instance.get<LAudio>().map(LItem::idValue),
//                                id = it.idValue(),
//                                start = true
//                            ).action()
//                        }
//                    },
//                    onNavigateToDetail = {
//                        val imageLoader = SingletonImageLoader.get(context)
//                        val coverMemoryKey = imageLoader.components.key(it, Options(context))
//
//                        AppRouter.route("/song/detail")
//                            .with("mediaId", it.idValue())
//                            .with("song", it)
//                            .with("coverCacheKey", coverMemoryKey)
//                            .jump()
//                    }
//                )
            }

            divider()
        }
    }
}