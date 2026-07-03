package com.lalilu.lhome.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun <I> RecommendRow(
    modifier: Modifier = Modifier,
    items: () -> List<I>,
    getId: (I) -> Any,
    scrollToFirstWhenChange: Boolean = false,
    itemContent: @Composable LazyItemScope.(item: I) -> Unit,
) {
    val listState = rememberLazyListState()

    // 当列表发生改变的时候触发滚动到第一个元素
    if (scrollToFirstWhenChange) {
        val currentItems = remember { mutableStateOf(items()) }

        LaunchedEffect(items()) {
            val items = items()
            var changed = false
            for (index in items.indices) {
                if (getId(items[index]) != getId(currentItems.value[index])) {
                    changed = true
                }
            }
            currentItems.value = items
            if (changed) {
                listState.scrollToItem(0)
            }
        }
    }

    LazyRow(
        modifier = modifier
            .fillMaxWidth(),
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(15.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
    ) {
        items(
            items = items(),
            key = getId,
            itemContent = itemContent
        )
    }
}
