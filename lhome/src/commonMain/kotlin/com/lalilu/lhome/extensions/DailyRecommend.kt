package com.lalilu.lhome.extensions

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import co.touchlab.kermit.Logger
import com.lalilu.component.LazyGridContent
import com.lalilu.component.LocalWindowSizeClass
import com.lalilu.krouter.KRouter
import com.lalilu.lhome.component.RecommendCard2
import com.lalilu.lhome.component.RecommendRow
import com.lalilu.lhome.component.RecommendTitle
import com.lalilu.lhome.viewmodel.HomeScreenModel
import com.lalilu.lmedia.entity.LAudio
import io.github.hristogochev.vortex.navigator.LocalNavigator
import io.github.hristogochev.vortex.screen.Screen
import org.koin.compose.koinInject

object DailyRecommend : LazyGridContent {

    @Composable
    override fun register(): LazyGridScope.() -> Unit {
        val windowWidthClass = LocalWindowSizeClass.current.widthSizeClass
        val navigator = LocalNavigator.current
        val homeVM = koinInject<HomeScreenModel>()

        SideEffect {
            Logger.i("windowWidthClass: $windowWidthClass")
        }

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
                            KRouter.route<Screen>("/detail", mapOf("item" to "test"))
                                ?.let { navigator?.push(it) }
                        },
                        label = {
                            Text("换一换")
                        }
                    )
                }
            }

            dailyRecommendForSideCompat(audios = { homeVM.dailyRecommends.value })
//            when (windowWidthClass) {
//                WindowWidthSizeClass.Compact -> dailyRecommendForSideCompat()
//                WindowWidthSizeClass.Medium -> dailyRecommendForSideMedium()
//                WindowWidthSizeClass.Expanded -> dailyRecommendForSideExpanded()
//            }
        }
    }
}

fun LazyGridScope.dailyRecommendForSideCompat(
    audios: () -> List<LAudio>
) {
    item(
        key = "daily_recommend",
        contentType = "daily_recommend",
        span = { GridItemSpan(maxLineSpan) }
    ) {
        RecommendRow(
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