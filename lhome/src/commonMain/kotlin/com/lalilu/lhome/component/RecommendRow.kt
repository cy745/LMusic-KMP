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

    if (scrollToFirstWhenChange) {
        LaunchedEffect(items()) {
            listState.scrollToItem(0)
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
