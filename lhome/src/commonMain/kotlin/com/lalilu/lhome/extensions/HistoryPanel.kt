package com.lalilu.lhome.extensions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.items
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
import com.lalilu.lhome.component.AudioItemCard
import com.lalilu.lhome.component.RecommendTitle
import com.lalilu.lhome.viewmodel.HomeScreenModel
import com.lalilu.navigation.LocalBackStack
import com.lalilu.navigation.Screen
import org.koin.compose.viewmodel.koinViewModel

object HistoryPanel : LazyGridContent {
    @Composable
    override fun register(): LazyGridScope.() -> Unit {
        val backStack = LocalBackStack.current
        val vm = koinViewModel<HomeScreenModel>()
        val items by vm.histories

        return fun LazyGridScope.() {
            item(span = { GridItemSpan(12) }) {
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
                                text = "历史播放",
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    )
                }
            }

            items(
                items = items,
                key = { it.id },
                contentType = { "HISTORY_ITEM" },
                span = { GridItemSpan(12) }
            ) {
                AudioItemCard(
                    modifier = Modifier
                        .clickable {

                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    title = it.title,
                    subtitle = it.subtitle,
                    imageData = it
                )
            }
        }
    }
}