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
import coil3.compose.LocalPlatformContext
import com.lalilu.component.LazyGridContent
import com.lalilu.extensions.SharedMap
import com.lalilu.extensions.retrieveCacheKey
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
                    val coverMemoryKey = context.retrieveCacheKey(item)

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
        if (items.invoke().isEmpty()) {
            Text(
                modifier = Modifier.fillMaxWidth()
                    .padding(vertical = 32.dp),
                text = "暂无数据",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium
            )
            return@item
        }

        RecommendRow(
            modifier = Modifier,
            items = items,
            getId = { it.idValue() },
            scrollToFirstWhenChange = true
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
