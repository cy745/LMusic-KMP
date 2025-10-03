package com.lalilu.lhome.extensions

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.animateBounds
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lalilu.component.LazyGridContent
import com.lalilu.lhome.component.RecommendCard
import com.lalilu.lhome.component.RecommendGroupCard
import com.lalilu.lhome.component.RecommendRow
import com.lalilu.lhome.component.RecommendTitle
import com.lalilu.lhome.viewmodel.HomeScreenModel
import com.lalilu.lmedia.entity.LGroupItem
import com.lalilu.lmedia.entity.LItem
import com.lalilu.navigation.LocalBackStack
import com.lalilu.navigation.LocalSharedTransitionScope
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
                RecommendTitle(
                    modifier = Modifier.padding(vertical = 8.dp),
                    title = "每日推荐",
                    onClick = {
                    }
                ) {
                    FilterChip(
                        selected = true,
                        onClick = { homeVM.requireUpdateDailyRecommends() },
                        label = {
                            Text("换一换")
                        }
                    )
                }
            }

            dailyRecommendForSideCompat(
                items = { homeVM.dailyRecommends.value },
                onClick = {

                }
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
fun LazyGridScope.dailyRecommendForSideCompat(
    items: () -> List<LItem>,
    onClick: (LItem) -> Unit = {}
) {
    item(
        key = "daily_recommend",
        contentType = "daily_recommend",
        span = { GridItemSpan(maxLineSpan) }
    ) {
        RecommendRow(
            modifier = Modifier.animateBounds(LocalSharedTransitionScope.current),
            items = items,
            getId = { it.id }
        ) {
            if (it is LGroupItem) {
                RecommendGroupCard(
                    modifier = Modifier.width(width = 250.dp),
                    group = it,
                    onClick = onClick
                )
            } else {
                RecommendCard(
                    modifier = Modifier.width(width = 250.dp),
                    title = it.title,
                    subTitle = it.subtitle,
                    imageData = it,
                    onClick = { onClick(it) }
                )
            }
        }
    }
}