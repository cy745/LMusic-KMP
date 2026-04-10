package com.lalilu.lhome.extensions

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.request.Options
import com.lalilu.component.LazyGridContent
import com.lalilu.extensions.SharedMap
import com.lalilu.lhome.component.RecommendCard
import com.lalilu.lhome.component.RecommendGroupCard
import com.lalilu.lhome.component.RecommendRow
import com.lalilu.lhome.component.RecommendTitle
import com.lalilu.lhome.viewmodel.HomeScreenModel
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.lmedia.entity.LItem
import com.lalilu.lmedia.entity.Linkable
import com.lalilu.lmedia.entity.ref
import com.lalilu.navigation.AppRouter
import org.koin.compose.viewmodel.koinViewModel

object DailyRecommend : LazyGridContent {

    @Composable
    override fun register(): LazyGridScope.() -> Unit {
        val homeVM = koinViewModel<HomeScreenModel>()
        val context = LocalPlatformContext.current

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
                items = { homeVM.dailyRecommends.value },
                onClick = { item, sharedMap ->
                    val imageLoader = SingletonImageLoader.get(context)
                    val coverMemoryKey = imageLoader.components.key(item, Options(context))

                    AppRouter.route("/song/detail")
                        .with("mediaId", item.idValue())
                        .with("sharedMap", sharedMap)
                        .with("song", item as? LAudio)
                        .with("coverCacheKey", coverMemoryKey)
                        .jump()
                }
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
fun LazyGridScope.dailyRecommendForSideCompat(
    items: () -> List<LItem>,
    onClick: (LItem, SharedMap) -> Unit = { _, _ -> }
) {
    item(
        key = "daily_recommend",
        contentType = "daily_recommend",
        span = { GridItemSpan(maxLineSpan) }
    ) {
        RecommendRow(
            modifier = Modifier,
            items = items,
            getId = { it.idValue() }
        ) { item ->
            if (item is Linkable && item.ref<LAudio>().isNotEmpty()) {
                RecommendGroupCard(
                    modifier = Modifier.width(width = 250.dp),
                    group = item,
                    onClick = onClick
                )
            } else {
                RecommendCard(
                    modifier = Modifier.width(width = 250.dp),
                    id = item.idValue(),
                    title = item.titleValue(),
                    subTitle = item.subtitleValue(),
                    imageData = item,
                    onClick = { onClick(item, it) }
                )
            }
        }
    }
}
