package com.lalilu.lhome.extensions

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lalilu.component.LazyGridContent
import com.lalilu.extensions.SharedMap
import com.lalilu.lhome.component.RecommendCard
import com.lalilu.lhome.component.RecommendRow
import com.lalilu.lhome.component.RecommendTitle
import com.lalilu.lhome.viewmodel.HomeScreenModel
import com.lalilu.lmedia.domain.model.LAudio
import com.lalilu.lmedia.domain.usecase.RecommendItem
import com.lalilu.navigation.AppRouter
import org.koin.compose.viewmodel.koinViewModel

object DailyRecommend : LazyGridContent {

    @Composable
    override fun register(): LazyGridScope.() -> Unit {
        val homeVM = koinViewModel<HomeScreenModel>()
        val dailyRecommends = homeVM.dailyRecommends.collectAsStateWithLifecycle()

        return fun LazyGridScope.() {
            item(
                key = "daily_recommend_header",
                contentType = "daily_recommend_header",
                span = { GridItemSpan(maxLineSpan) }
            ) {
                RecommendTitle(title = "每日推荐") {
                    FilterChip(
                        selected = true,
                        shape = RoundedCornerShape(50),
                        onClick = { homeVM.requireUpdateDailyRecommends() },
                        label = {
                            Text(
                                text = "换一换",
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    )
                }
            }

            dailyRecommendForSideCompat(
                items = { dailyRecommends.value },
                onClick = { item, sharedMap ->
                    val id = when (item) {
                        is RecommendItem.Audio -> item.audio.id
                        is RecommendItem.Album -> item.album.id
                        is RecommendItem.Artist -> item.artist.id
                    }

                    AppRouter.route("/song/detail")
                        .with("mediaId", id)
                        .with("sharedMap", sharedMap)
                        .with("song", (item as? RecommendItem.Audio)?.audio)
                        .with("coverCacheKey", "")
                        .jump()
                }
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
fun LazyGridScope.dailyRecommendForSideCompat(
    items: () -> List<RecommendItem>,
    onClick: (RecommendItem, SharedMap) -> Unit = { _, _ -> }
) {
    item(
        key = "daily_recommend",
        contentType = "daily_recommend",
        span = { GridItemSpan(maxLineSpan) }
    ) {
        val list = items()
        if (list.isEmpty()) {
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

        RecommendRow(
            modifier = Modifier,
            items = { list },
            getId = { item ->
                when (item) {
                    is RecommendItem.Audio -> item.audio.id
                    is RecommendItem.Album -> item.album.id
                    is RecommendItem.Artist -> item.artist.id
                }
            },
            scrollToFirstWhenChange = true
        ) { item ->
            val id = when (item) {
                is RecommendItem.Audio -> item.audio.id
                is RecommendItem.Album -> item.album.id
                is RecommendItem.Artist -> item.artist.id
            }
            val title = when (item) {
                is RecommendItem.Audio -> item.audio.title
                is RecommendItem.Album -> item.album.title
                is RecommendItem.Artist -> item.artist.title
            }
            val subtitle = when (item) {
                is RecommendItem.Audio -> item.audio.subtitle
                is RecommendItem.Album -> item.album.subtitle
                is RecommendItem.Artist -> item.artist.subtitle
            }

            val imageData = when (item) {
                is RecommendItem.Audio -> item.audio
                is RecommendItem.Album -> item.album
                is RecommendItem.Artist -> item.artist
            }
            RecommendCard(
                modifier = Modifier.width(width = 250.dp),
                id = id,
                title = title,
                subTitle = subtitle,
                imageData = imageData,
                onClick = { onClick(item, it) }
            )
        }
    }
}
