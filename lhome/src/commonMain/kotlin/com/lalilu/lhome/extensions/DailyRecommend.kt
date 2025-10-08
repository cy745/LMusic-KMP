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
import com.lalilu.component.LazyGridContent
import com.lalilu.extensions.SharedMap
import com.lalilu.krouter.KRouter
import com.lalilu.lhome.component.RecommendCard
import com.lalilu.lhome.component.RecommendGroupCard
import com.lalilu.lhome.component.RecommendRow
import com.lalilu.lhome.component.RecommendTitle
import com.lalilu.lhome.viewmodel.HomeScreenModel
import com.lalilu.lmedia.entity.LGroupItem
import com.lalilu.lmedia.entity.LItem
import com.lalilu.navigation.LocalBackStack
import com.lalilu.navigation.Screen
import org.koin.compose.viewmodel.koinViewModel

object DailyRecommend : LazyGridContent {

    @Composable
    override fun register(): LazyGridScope.() -> Unit {
        val navigator = LocalBackStack.current
        val homeVM = koinViewModel<HomeScreenModel>()

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
                    val screen = runCatching {
                        KRouter.route<Screen>(
                            router = "/song/detail",
                            extraParams = mapOf(
                                "mediaId" to item.id,
                                "sharedMap" to sharedMap
                            )
                        )
                    }.getOrNull()
                        ?: return@dailyRecommendForSideCompat

                    navigator.add(screen)
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
            getId = { it.id }
        ) { item ->
            if (item is LGroupItem) {
                RecommendGroupCard(
                    modifier = Modifier.width(width = 250.dp),
                    group = item,
                    onClick = onClick
                )
            } else {
                RecommendCard(
                    modifier = Modifier.width(width = 250.dp),
                    id = item.id,
                    title = item.title,
                    subTitle = item.subtitle,
                    imageData = item,
                    onClick = { onClick(item, it) }
                )
            }
        }
    }
}