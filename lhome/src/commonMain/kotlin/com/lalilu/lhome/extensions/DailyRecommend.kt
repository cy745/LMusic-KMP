package com.lalilu.lhome.extensions

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.animateBounds
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lalilu.component.LazyGridContent
import com.lalilu.krouter.KRouter
import com.lalilu.lhome.component.RecommendCard2
import com.lalilu.lhome.component.RecommendRow
import com.lalilu.lhome.component.RecommendTitle
import com.lalilu.lhome.viewmodel.HomeScreenModel
import com.lalilu.lmedia.entity.LAudio
import com.lalilu.navigation.LocalBackStack
import com.lalilu.navigation.LocalSharedTransitionScope
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
                RecommendTitle(
                    modifier = Modifier.padding(vertical = 8.dp),
                    title = "每日推荐",
                    onClick = {
                    }
                ) {
                    FilterChip(
                        selected = true,
                        onClick = {
                            KRouter.route<Screen>("/player", mapOf("item" to "test"))
                                ?.let { navigator.add(it) }
                        },
                        label = {
                            Text("换一换")
                        }
                    )
                }
            }

            dailyRecommendForSideCompat(audios = { homeVM.recentlyAdded.value })
//            when (windowWidthClass) {
//                WindowWidthSizeClass.Compact -> dailyRecommendForSideCompat()
//                WindowWidthSizeClass.Medium -> dailyRecommendForSideMedium()
//                WindowWidthSizeClass.Expanded -> dailyRecommendForSideExpanded()
//            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
fun LazyGridScope.dailyRecommendForSideCompat(
    audios: () -> List<LAudio>
) {
    item(
        key = "daily_recommend",
        contentType = "daily_recommend",
        span = { GridItemSpan(maxLineSpan) }
    ) {
        RecommendRow(
            modifier = Modifier.animateBounds(LocalSharedTransitionScope.current),
            items = audios,
            getId = { it.id }
        ) {
            RecommendCard2(
                item = { it },
                modifier = Modifier.size(width = 250.dp, height = 250.dp),
                onClick = {
//                    AppRouter.route("/pages/songs/detail")
//                        .with("mediaId", it.id)
//                        .jump()
                }
            )
        }
    }
}

fun LazyGridScope.dailyRecommendForSideMedium() {
}

fun LazyGridScope.dailyRecommendForSideExpanded() {

}