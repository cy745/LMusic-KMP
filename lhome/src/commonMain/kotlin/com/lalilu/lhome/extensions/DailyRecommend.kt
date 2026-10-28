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
import com.lalilu.component.LazyGridContent
import com.lalilu.extensions.SharedMap
import com.lalilu.lhome.component.RecommendCard
import com.lalilu.lmedia.isAudioPlayable
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
        val dailyRecommends = homeVM.dailyRecommends

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
                queryState = dailyRecommends,
                onClick = { item, sharedMap ->
                    when (item) {
                        is RecommendItem.Audio -> AppRouter.route("/song/detail")
                            .with("mediaId", item.audio.playbackId).with("song", item.audio)
                        is RecommendItem.Album -> AppRouter.route("/pages/albums/detail")
                            .with("albumId", item.album.id).with("album", item.album)
                        is RecommendItem.Artist -> AppRouter.route("/pages/artists/detail")
                            .with("artistId", item.artist.id).with("artist", item.artist)
                    }.with("sharedMap", sharedMap).jump()
                }
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
fun LazyGridScope.dailyRecommendForSideCompat(
    items: () -> List<RecommendItem>,
    queryState: com.lalilu.extensions.LibraryQueryState<*>? = null,
    onClick: (RecommendItem, SharedMap) -> Unit = { _, _ -> }
) {
    item(
        key = "daily_recommend",
        contentType = "daily_recommend",
        span = { GridItemSpan(maxLineSpan) }
    ) {
        val list = items()
        if (list.isEmpty()) {
            val empty: @Composable () -> Unit = {
                com.lalilu.component.LibraryEmptyState("等待发现你的音乐", "曲库准备好后，来这里发现想听的歌曲")
            }
            if (queryState != null) com.lalilu.component.LibraryQueryContent(queryState, content = empty)
            else empty()
            return@item
        }

        RecommendRow(
            modifier = Modifier,
            items = { list },
            getId = { item ->
                when (item) {
                    is RecommendItem.Audio -> item.audio.playbackId
                    is RecommendItem.Album -> item.album.id
                    is RecommendItem.Artist -> item.artist.id
                }
            },
            scrollToFirstWhenChange = true
        ) { item ->
            val id = when (item) {
                is RecommendItem.Audio -> item.audio.playbackId
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
                enabled = if (item is RecommendItem.Audio) isAudioPlayable(item.audio) else true,
                onClick = { onClick(item, it) }
            )
        }
    }
}
