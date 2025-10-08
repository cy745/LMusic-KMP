package com.lalilu.lhome.extensions

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lalilu.component.LazyGridContent
import com.lalilu.krouter.KRouter
import com.lalilu.lhome.component.RecommendCard
import com.lalilu.lhome.component.RecommendRow
import com.lalilu.lhome.component.RecommendTitle
import com.lalilu.lhome.viewmodel.HomeScreenModel
import com.lalilu.navigation.LocalBackStack
import com.lalilu.navigation.Screen
import org.koin.compose.viewmodel.koinViewModel

object LatestPanel : LazyGridContent {

    @Composable
    override fun register(): LazyGridScope.() -> Unit {
        val backStack = LocalBackStack.current
        val vm = koinViewModel<HomeScreenModel>()
        val items by vm.recentlyAdded

        return fun LazyGridScope.() {
            // 若列表为空，不显示
            if (items.isEmpty()) return

            item(
                key = "latest_header",
                contentType = "latest_header",
                span = { GridItemSpan(maxLineSpan) }
            ) {
                RecommendTitle(title = "最近添加") {
                    FilterChip(
                        selected = true,
                        shape = RoundedCornerShape(50),
                        onClick = {
                            runCatching { KRouter.route<Screen>("/pages/songs") }
                                .getOrNull()
                                ?.let { backStack.add(it) }
                        },
                        label = {
                            Text(
                                text = "所有歌曲",
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    )
                }
            }

            item(
                key = "latest",
                contentType = "latest",
                span = { GridItemSpan(maxLineSpan) }
            ) {
                RecommendRow(
                    items = { items },
                    getId = { it.id }
                ) { item ->
                    RecommendCard(
                        modifier = Modifier.width(120.dp),
                        id = item.id,
                        title = item.title,
                        subTitle = item.subtitle,
                        imageData = item,
                        onClick = { sharedMap ->
                            val screen = runCatching {
                                KRouter.route<Screen>(
                                    router = "/song/detail",
                                    extraParams = mapOf(
                                        "mediaId" to item.id,
                                        "sharedMap" to sharedMap
                                    )
                                )
                            }.getOrNull()
                                ?: return@RecommendCard

                            backStack.add(screen)
                        }
                    )
                }
            }
        }
    }
}